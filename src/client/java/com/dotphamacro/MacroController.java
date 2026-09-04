package com.dotphamacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Quan ly toan bo trang thai cua macro:
 *
 *  IDLE            -> nhan phim toggle -> ARMED
 *  ARMED           -> nguoi choi left-click khi dang cam item o hotbar slot 1
 *                     -> luu lai item (Item + custom name) -> gui /dotpha -> RUNNING_DOTPHA
 *  RUNNING_DOTPHA  -> chat bao "that bai" hoac "thanh cong len"
 *                     -> kiem tra item con dung khong -> gui lai /dotpha (giu nguyen state)
 *                   -> chat bao "HAY DUNG /dokiep..."
 *                     -> kiem tra item -> dung bua (right-click) -> /dokiep -> RUNNING_DOKIEP
 *  RUNNING_DOKIEP  -> chat bao that bai do kiep -> kiem tra item -> gui /dotpha -> RUNNING_DOTPHA
 *                   -> chat bao do kiep thanh cong / dot pha canh gioi / song sot
 *                     -> kiem tra item -> gui /dotpha -> RUNNING_DOTPHA
 *
 * O bat ky buoc nao neu item dang cam khac voi item da luu (doi item hoac
 * khong con o hotbar slot 1), macro se TU DONG TAT de tranh gui lenh nham.
 *
 * Moi hanh dong (dung item / gui lenh) di qua 1 hang doi co delay giua cac
 * buoc (xem COMMAND_DELAY_TICKS), thay vi ban lien tuc trong cung 1 tick -
 * tranh truong hop server chua kip xu ly buoc truoc (vd nhan vat con dang
 * "chet" do buoc truoc) da nhan lenh buoc sau.
 */
public class MacroController {

	private static final int TRACKED_SLOT = 0; // hotbar slot "1" = index 0

	/** So tick cho giua 2 hanh dong lien tiep (20 tick = 1 giay). */
	private static final int COMMAND_DELAY_TICKS = 12; // ~0.6 giay

	public enum State {
		IDLE,
		ARMED,
		RUNNING_DOTPHA,
		RUNNING_DOKIEP
	}

	// ----- cac mau chat can nhan dien (chi can viet dang thuong, vi moi tin -----
	// ----- nhan da duoc "giai ma" font cach dieu ve chu Latin thuong truoc)  -----

	private static final String[] DOTPHA_RETRY_PATTERNS = {
			"đột phá thất bại",
			"đột phá thành công lên"
	};

	private static final String[] DOKIEP_PROMPT_PATTERNS = {
			"để vượt qua thiên kiếp"
	};

	private static final String[] DOKIEP_FAIL_PATTERNS = {
			"thất bại trong độ lôi kiếp"
	};

	private static final String[] DOKIEP_SUCCESS_PATTERNS = {
			"độ kiếp thành công",
			"đột phá cảnh giới",
			"sống sót qua độ lôi kiếp"
	};

	// Cac ky tu Unicode "small caps" / gia dang (Cyrillic trong nhin giong Latin)
	// ma server hay dung de "cach dieu" chu -> map nguoc ve chu Latin thuong.
	private static final Map<Character, Character> STYLE_MAP = buildStyleMap();

	private static Map<Character, Character> buildStyleMap() {
		Map<Character, Character> m = new HashMap<>();
		String stylized = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";
		String normal   = "abcdefghijklmnopqrstuvwxyz";
		for (int i = 0; i < stylized.length(); i++) {
			m.put(stylized.charAt(i), normal.charAt(i));
		}
		return m;
	}

	private State state = State.IDLE;

	// Item duoc "khoa" khi bat dau macro, dung de so sanh truoc moi lan gui lenh
	private Item trackedItem = null;
	private String trackedCustomName = null; // null = item khong co custom name

	// Theo doi trang thai nhan/nha cua attack key o tick truoc, de tu do "rising edge"
	// (KHONG dung attackKey.wasPressed() vi vanilla Minecraft da tu tieu thu counter do
	// trong handleInputEvents() truoc khi END_CLIENT_TICK cua mod chay, khien wasPressed()
	// luon tra ve false o day)
	private boolean attackKeyWasDown = false;

	// Hang doi cac hanh dong (gui lenh / dung item) can thuc hien tuan tu, moi
	// hanh dong cach nhau COMMAND_DELAY_TICKS tick.
	private final ArrayDeque<Runnable> pendingActions = new ArrayDeque<>();
	private int cooldownTicks = 0;

	// ---------------------------------------------------------------------

	public void onClientTick(MinecraftClient client) {
		if (client.player == null) {
			if (state != State.IDLE) disable(null);
			return;
		}

		// Xu ly nhan phim toggle (wasPressed la edge-triggered, an toan trong tick loop
		// vi khong co code vanilla nao khac tieu thu counter cua phim ']')
		while (DotPhaMacroClient.TOGGLE_KEY.wasPressed()) {
			toggle(client);
		}

		// Tu do click chuot trai bang edge-detect thu cong tren isPressed()
		boolean attackKeyDown = client.options.attackKey.isPressed();
		boolean justClicked = attackKeyDown && !attackKeyWasDown;
		attackKeyWasDown = attackKeyDown;

		if (state == State.ARMED && justClicked) {
			tryArmStart(client);
		}

		// Xu ly hang doi hanh dong (co delay giua cac buoc)
		if (cooldownTicks > 0) {
			cooldownTicks--;
		} else if (!pendingActions.isEmpty()) {
			Runnable action = pendingActions.poll();
			action.run();
			cooldownTicks = COMMAND_DELAY_TICKS;
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
		enqueueCommand("dotpha");
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

			// Dung bua (right-click item) roi /dokiep - KHONG dung /tusat nua
			// (tu sat lam nhan vat "chet" ngay lap tuc, khien /dokiep gui ngay
			// sau do bi tu choi vi dang o trang thai chet)
			pendingActions.clear();
			enqueueUseTrackedItem();
			enqueueCommand("dokiep");
			state = State.RUNNING_DOKIEP;
			return;
		}

		if (containsAny(plain, DOTPHA_RETRY_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			enqueueCommand("dotpha");
		}
	}

	private void handleRunningDoKiep(MinecraftClient client, String plain) {
		// Tin bao ket qua do kiep thuong la broadcast toan server kem ten nguoi
		// choi (vd "jokhehe da that bai trong do loi kiep..."), nen phai loc xem
		// co phai chinh minh khong, tranh nhan nham ket qua cua nguoi khac.
		if (!mentionsMe(client, plain)) return;

		if (containsAny(plain, DOKIEP_FAIL_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			enqueueCommand("dotpha");
			state = State.RUNNING_DOTPHA;
			return;
		}

		if (containsAny(plain, DOKIEP_SUCCESS_PATTERNS)) {
			if (!verifyTrackedItemStillHeld(client)) return;
			enqueueCommand("dotpha");
			state = State.RUNNING_DOTPHA;
		}
	}

	// ---------------------------------------------------------------------

	/**
	 * Kiem tra tin nhan (da normalize/de-stylize) co lien quan den chinh nguoi
	 * choi hay khong:
	 * - Tin ca nhan (server dung tu "bạn") -> luon coi la lien quan.
	 * - Tin broadcast toan server kem ten nguoi choi -> chi coi la lien quan
	 *   neu chua ten (username) cua chinh minh.
	 */
	private static boolean mentionsMe(MinecraftClient client, String plain) {
		if (plain.contains("bạn ")) return true;
		String username = client.getSession().getUsername();
		if (username == null || username.isEmpty()) return true; // khong xac dinh duoc -> khong chan
		return plain.contains(username.toLowerCase(Locale.forLanguageTag("vi")));
	}

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

	private void enqueueUseTrackedItem() {
		pendingActions.add(() -> useTrackedItem(MinecraftClient.getInstance()));
	}

	private void enqueueCommand(String command) {
		pendingActions.add(() -> sendCommand(MinecraftClient.getInstance(), command));
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
		pendingActions.clear();
		cooldownTicks = 0;
		if (wasActive && reasonMessage != null) {
			DotPhaMacroClient.sendLocalMessage(reasonMessage);
		}
	}

	// ---------------------------------------------------------------------

	private static String extractCustomName(ItemStack stack) {
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		return name == null ? null : normalize(name.getString());
	}

	private static boolean equalsNullable(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	/** Ha chu thuong + "giai ma" font cach dieu (small caps / Cyrillic gia dang) ve Latin thuong. */
	private static String normalize(String s) {
		String lower = s.toLowerCase(Locale.forLanguageTag("vi"));
		StringBuilder sb = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			sb.append(STYLE_MAP.getOrDefault(c, c));
		}
		return sb.toString();
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
