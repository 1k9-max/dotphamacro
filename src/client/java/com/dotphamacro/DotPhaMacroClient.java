package com.dotphamacro;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * DotPha Macro - macro client-side tu dong hoa vong lap "dot pha / do kiep"
 * dua theo noi dung chat nhan duoc tu server.
 *
 * Toan bo logic chi chay tren may client, khong doc/ghi file server,
 * khong can mixin - chi dung Fabric API hooks + cac API cong khai.
 */
public class DotPhaMacroClient implements ClientModInitializer {

	public static final String MOD_ID = "dotphamacro";

	/** Phim tat mac dinh: ']' */
	public static KeyBinding TOGGLE_KEY;

	private final MacroController controller = new MacroController();

	@Override
	public void onInitializeClient() {
		TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.dotphamacro.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_BRACKET, // phim ']'
				"key.categories.dotphamacro"
		));

		// Vong lap tick client: xu ly phim tat toggle + cho left-click khi da "armed"
		ClientTickEvents.END_CLIENT_TICK.register(controller::onClientTick);

		// Lang nghe moi tin nhan chat / he thong tu server (bo qua actionbar overlay)
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			controller.onChatMessage(message);
		});

		// Tu dong tat macro khi roi server / disconnect
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> controller.disable(null));
	}

	public static void sendLocalMessage(Text text) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(text, false);
		}
	}
}
