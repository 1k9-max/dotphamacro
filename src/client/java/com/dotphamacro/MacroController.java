package com.dotphamacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.Locale;

/**
 * Quan ly toan bo trang thai cua macro:
 *
 *  IDLE            -> nhan phim toggle -> ARMED
 *  ARMED           -> nguoi choi left-click khi dang cam item o hotbar slot 1
 *                     -> luu lai item (Item + custom name) -> gui /dotpha -> RUNNING_DOTPHA
 *  RUNNING_DOTPHA  -> chat bao "that bai" hoac "thanh cong len" (cung cap)
 *                     -> kiem tra item con dung khong -> gui lai /dotpha (giu nguyen state)
 *                   -> chat bao "HAY DUNG /dokiep..."
 *                     -> kiem tra item -> right-click item + /tusat + /dokiep -> RUNNING_DOKIEP
 *  RUNNING_DOKIEP  -> chat bao that bai do kiep -> kiem tra item -> gui /dotpha -> RUNNING_DOTPHA
 *                   -> chat bao do kiep thanh cong / dot pha canh gioi / song sot
 *                     -> kiem tra item -> gui /dotpha -> RUNNING_DOTPHA
 *
 * O bat ky buoc nao neu item dang cam khac voi item da luu (doi item hoac
 * khong con o hotbar slot 1), macro se TU DONG TAT de tranh gui lenh nham.
 */
public class MacroController {

	private static final int TRACKED_SLOT = 0; // hotbar slot "1" = index 0

	public enum State {
		IDLE,
		ARMED,
		RUNNING_DOTPHA,
		RUNNING_DOKIEP
	}

	// ----- cac mau chat can nhan dien (ban thuong + ban ky tu cach dieu) -----

	private static final String[] DOTPHA_RETRY_PATTERNS = {
			"đột phá thất bại",
			"độᴛ ᴘʜá ᴛʜấᴛ ʙạɪ",
			"đột phá thành công lên",
			"độᴛ ᴘʜá ᴛʜàɴʜ ᴄôɴɢ ʟêɴ"
	};

	private static final String[] DOKIEP_PROMPT_PATTERNS = {
			"hãy dùng /dokiep để vượt qua thiên kiếp",
			"ʜãʏ ᴅùɴɢ /dokiep để vượt qua thiên kiếp"
	};

	private static final String[] DOKIEP_FAIL_PATTERNS = {
			"thất bại trong độ lôi kiếp",
			"ᴛʜấᴛ ʙạɪ ᴛʀᴏɴɢ độ ʟôɪ ᴋɪếᴘ"
	};

	private static final String[] DOKIEP_SUCCESS_PATTERNS = {
			"độ kiếp thành công",
			"độ ᴋɪếᴘ ᴛʜàɴʜ ᴄôɴɢ",
			"độᴛ ᴘʜá ᴄảɴʜ ɢɪớɪ",
			"đột phá cảnh giới",
			"sóɴɢ sóᴛ ǫᴜᴀ độ ʟôɪ ᴋɪếᴘ",
			"sống sót qua độ lôi kiếp"
	};

	private State state = State.IDLE;

	// Item duoc "khoa" khi bat dau macro, dung de so sanh truoc moi lan gui lenh
	private Item trackedItem = null;
	private String trackedCustomName = null; // null = item khong co custom name

	// ---------------------------------------------------------------------

	public void onClientTick(MinecraftClient client) {
		if (client.player == null) {
			if (state != State.IDLE) disable(null);
			return;
		}

		// Xu ly nhan phim toggle (wasPressed la edge-triggered, an toan trong tick loop)
		while (DotPhaMacroClient.TOGGLE_KEY.wasPressed()) {
			toggle(client);
		}

		if (state == State.ARMED) {
			// Cho nguoi choi left-click (attack key) de xac nhan bat dau
			if (client.options.attackKey.wasPressed()) {
				tryArmStart(client);
			}
		}
	}

	private void toggle(MinecraftClient client) {
		if (state == State.IDLE) {
			state = State.ARMED;
			DotPhaMacroClient.sendLocalMessage(
					Text.literal("[DotPha Macro] Da BAT. Cam item o hotbar slot 1 roi click chuot trai de bat dau.")
							.formatted(Formatting.GREEN));
		} else {
			disable(Text.literal("[DotPha Macro] Da TAT (thu cong).").formatted(Formatting.RED));
		}
	}

	private void tryArmStart(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) return;

		if (player.getInventory().selectedSlot != TRACKED_SLOT) {
			DotPhaMacroClient.sendLocalMessage(
					Text.literal("[DotPha Macro] Hay chon hotbar slot 1 truoc khi click chuot trai.")
							.formatted(Formatting.YELLOW));
			return;
		}

		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) {
			DotPhaMacroClient.sendLocalMessage(
					Text.literal("[DotPha Macro] Slot 1 dang trong. Hay cam item can dung roi thu lai.")
							.formatted(Formatting.YELLOW));
			return;
		}

		trackedItem = stack.getItem();
		trackedCustomName = extractCustomName(stack);

		DotPhaMacroClient.sendLocalMessage(
				Text.literal("[DotPha Macro] Da luu item: " + describeTracked())
						.formatted(Formatting.AQUA));

		state = State.RUNNING_DOTPHA;
		sendCommand(client, "dotpha");
	}

	public void onChatMessage(Text message) {
		if (state == State.IDLE || state == State.ARMED) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		String plain = normalize(message.getString());

		switch (state) {
			case RUNNING_DOTPHA -> handleRunningDotPha(client, plain);
			case RUNNING_DOKIEP -> handleRunningDoKiep(client, plain);
			default -> { }
		}
	}

	private void handleRunningDotPha(MinecraftClient client, String plain) {
		if (containsAny(plain, DOKIEP_PROMPT_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;

			// chuot phai item (dung item) -> /tusat -> /dokiep
			useTrackedItem(client);
			sendCommand(client, "tusat");
			sendCommand(client, "dokiep");
			state = State.RUNNING_DOKIEP;
			return;
		}

		if (containsAny(plain, DOTPHA_RETRY_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			sendCommand(client, "dotpha");
		}
	}

	private void handleRunningDoKiep(MinecraftClient client, String plain) {
		if (containsAny(plain, DOKIEP_FAIL_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			sendCommand(client, "dotpha");
			state = State.RUNNING_DOTPHA;
			return;
		}

		if (containsAny(plain, DOKIEP_SUCCESS_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			sendCommand(client, "dotpha");
			state = State.RUNNING_DOTPHA;
		}
	}

	// ---------------------------------------------------------------------

	/**
	 * So sanh item dang cam o hotbar slot 1 voi item da luu khi bat macro.
	 * Neu khac (doi item, doi ten, hoac chuyen slot) -> tu dong tat macro.
	 */
	private boolean verifyTrackedItemStillHeld(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			disable(null);
			return false;
		}

		if (player.getInventory().selectedSlot != TRACKED_SLOT) {
			disable(Text.literal("[DotPha Macro] Da chuyen khoi hotbar slot 1 -> TU DONG TAT macro de an toan.")
					.formatted(Formatting.RED));
			return false;
		}

		ItemStack current = player.getMainHandStack();
		if (current.isEmpty() || current.getItem() != trackedItem
				|| !equalsNullable(extractCustomName(current), trackedCustomName)) {
			disable(Text.literal("[DotPha Macro] Phat hien item da thay doi (" + describeTracked()
							+ " -> " + describeStack(current) + ") -> TU DONG TAT macro de an toan.")
					.formatted(Formatting.RED));
			return false;
		}

		return true;
	}

	private void useTrackedItem(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.interactionManager == null) return;
		client.interactionManager.interactItem(player, Hand.MAIN_HAND);
		player.swingHand(Hand.MAIN_HAND);
	}

	private void sendCommand(MinecraftClient client, String command) {
		if (client.player == null || client.player.networkHandler == null) return;
		client.player.networkHandler.sendChatCommand(command);
	}

	public void disable(Text reasonMessage) {
		boolean wasActive = state != State.IDLE;
		state = State.IDLE;
		trackedItem = null;
		trackedCustomName = null;
		if (wasActive && reasonMessage != null) {
			DotPhaMacroClient.sendLocalMessage(reasonMessage);
		}
	}

	// ---------------------------------------------------------------------

	private static String extractCustomName(ItemStack stack) {
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	private static boolean equalsNullable(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private static String normalize(String s) {
		return s.toLowerCase(Locale.forLanguageTag("vi"));
	}

	private static boolean containsAny(String haystack, String[] needles) {
		for (String needle : needles) {
			if (haystack.contains(needle)) return true;
		}
		return false;
	}

	private String describeTracked() {
		return trackedCustomName != null ? trackedCustomName : (trackedItem != null ? trackedItem.toString() : "?");
	}

	private static String describeStack(ItemStack stack) {
		if (stack.isEmpty()) return "trong";
		String name = extractCustomName(stack);
		return name != null ? name : stack.getItem().toString();
	}
}
