package com.songmod;

import com.songmod.client.SongSelectScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** クライアント側の初期化: 選曲台の一覧画面を、サーバーからの合図で開く。 */
public class SongClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SongNetwork.OpenPayload.TYPE,
				(payload, context) -> Minecraft.getInstance().execute(
						() -> Minecraft.getInstance().setScreenAndShow(new SongSelectScreen(payload))));
	}
}
