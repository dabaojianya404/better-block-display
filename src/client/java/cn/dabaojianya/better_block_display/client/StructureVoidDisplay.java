package cn.dabaojianya.better_block_display.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class StructureVoidDisplay {
	private static final int RADIUS = 64;
	private static final int RADIUS_SQ = RADIUS * RADIUS;
	private static final int CHUNK_RADIUS = RADIUS / 16;
	private static final int RESCAN_INTERVAL = 10;
	private static final float QUAD_SIZE = 0.5F;

	private static final List<BlockPos> POSITIONS = new ArrayList<>();

	private static boolean wasHolding = false;
	private static int tickCounter = 0;

	private StructureVoidDisplay() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(StructureVoidDisplay::onClientTick);
		WorldRenderEvents.LAST.register(StructureVoidDisplay::onRenderLast);
	}

	private static void onClientTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		boolean holding = player != null
			&& level != null
			&& (player.getMainHandItem().is(Items.STRUCTURE_VOID)
				|| player.getOffhandItem().is(Items.STRUCTURE_VOID));

		if (!holding) {
			wasHolding = false;
			tickCounter = 0;
			if (!POSITIONS.isEmpty()) {
				POSITIONS.clear();
			}
			return;
		}

		tickCounter++;
		if (!wasHolding || tickCounter >= RESCAN_INTERVAL) {
			wasHolding = true;
			tickCounter = 0;
			scan(level, player);
		}
	}

	private static void scan(ClientLevel level, LocalPlayer player) {
		POSITIONS.clear();
		int px = player.getBlockX();
		int py = player.getBlockY();
		int pz = player.getBlockZ();
		int pcx = px >> 4;
		int pcz = pz >> 4;

		for (int cx = pcx - CHUNK_RADIUS; cx <= pcx + CHUNK_RADIUS; cx++) {
			for (int cz = pcz - CHUNK_RADIUS; cz <= pcz + CHUNK_RADIUS; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}

				LevelChunk chunk = level.getChunk(cx, cz);
				chunk.findBlocks(
					state -> state.is(Blocks.STRUCTURE_VOID),
					(pos, state) -> {
						int dx = pos.getX() - px;
						int dy = pos.getY() - py;
						int dz = pos.getZ() - pz;
						if (dx * dx + dy * dy + dz * dz <= RADIUS_SQ) {
							POSITIONS.add(pos.immutable());
						}
					}
				);
			}
		}
	}

	private static void onRenderLast(WorldRenderContext context) {
		if (POSITIONS.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}

		PoseStack matrices = context.matrixStack();
		if (matrices == null) {
			return;
		}

		Camera camera = context.camera();
		Vec3 camPos = camera.getPosition();

		TextureAtlasSprite sprite = mc.getBlockRenderer()
			.getBlockModelShaper()
			.getParticleIcon(Blocks.STRUCTURE_VOID.defaultBlockState());
		float u0 = sprite.getU0();
		float u1 = sprite.getU1();
		float v0 = sprite.getV0();
		float v1 = sprite.getV1();

		Quaternionf rotation = new Quaternionf(camera.rotation());
		Vector3f axisX = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation).mul(QUAD_SIZE);
		Vector3f axisY = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation).mul(QUAD_SIZE);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

		PoseStack modelView = RenderSystem.getModelViewStack();
		modelView.pushPose();
		modelView.setIdentity();
		modelView.mulPoseMatrix(matrices.last().pose());
		RenderSystem.applyModelViewMatrix();

		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

		for (BlockPos pos : POSITIONS) {
			float x = (float) (pos.getX() + 0.5 - camPos.x);
			float y = (float) (pos.getY() + 0.5 - camPos.y);
			float z = (float) (pos.getZ() + 0.5 - camPos.z);

			buffer.vertex(x - axisX.x - axisY.x, y - axisX.y - axisY.y, z - axisX.z - axisY.z).uv(u1, v1).endVertex();
			buffer.vertex(x - axisX.x + axisY.x, y - axisX.y + axisY.y, z - axisX.z + axisY.z).uv(u1, v0).endVertex();
			buffer.vertex(x + axisX.x + axisY.x, y + axisX.y + axisY.y, z + axisX.z + axisY.z).uv(u0, v0).endVertex();
			buffer.vertex(x + axisX.x - axisY.x, y + axisX.y - axisY.y, z + axisX.z - axisY.z).uv(u0, v1).endVertex();
		}

		Tesselator.getInstance().end();

		modelView.popPose();
		RenderSystem.applyModelViewMatrix();

		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}
}
