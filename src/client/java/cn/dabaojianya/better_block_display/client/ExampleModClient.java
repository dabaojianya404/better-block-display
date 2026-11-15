package cn.dabaojianya.better_block_display.client;

import net.fabricmc.api.ClientModInitializer;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		StructureVoidDisplay.register();
	}
}