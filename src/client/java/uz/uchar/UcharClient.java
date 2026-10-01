package uz.uchar;

import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Survival'da uchish (creative'dagidek: Space'ni ikki marta bosib uchasiz).
 *
 * Bir kishilik dunyoda: o'yinning ichki serverida uchish qobiliyati yoqiladi.
 * Serverda (masalan, Aternos): uchish faqat sizning tomoningizda yoqiladi.
 * Server sizni kick qilmasligi uchun unda allow-flight yoqilgan bo'lishi kerak.
 */
public class UcharClient implements ClientModInitializer {
	/** O'yindagi standart uchish tezligi 0.05. */
	private static final float BASE_SPEED = 0.05f;
	private static final float[] SPEEDS = {1f, 2f, 3f, 5f};

	private static KeyMapping toggleKey;
	private static KeyMapping speedKey;

	private static boolean enabled = false;
	private static int speedIndex = 1;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.uchar"));
		speedKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.uchar.speed", InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.uchar"));

		ClientTickEvents.END_CLIENT_TICK.register(UcharClient::onTick);
	}

	private static float speed() {
		return BASE_SPEED * SPEEDS[speedIndex];
	}

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			if (client.player == null) {
				continue;
			}

			enabled = !enabled;

			if (enabled) {
				message(client, "§aUchish YONIQ §7(Space'ni ikki marta bosing)");
			} else {
				turnOff(client);
				message(client, "§cUchish O'CHIQ");
			}
		}

		while (speedKey.consumeClick()) {
			speedIndex = (speedIndex + 1) % SPEEDS.length;
			message(client, "§eUchish tezligi: " + (int) SPEEDS[speedIndex] + "x");
		}

		if (!enabled) {
			return;
		}

		// Dunyodan chiqqanda avtomatik o'chadi.
		if (client.player == null || client.getConnection() == null) {
			enabled = false;
			return;
		}

		if (client.hasSingleplayerServer()) {
			turnOnSingleplayer(client);
		} else {
			turnOnMultiplayer(client);
		}
	}

	// ---------- Bir kishilik dunyo ----------

	/**
	 * Har tikda tekshiradi: o'lgandan keyin yoki Nether/End'ga o'tganda
	 * o'yin qobiliyatlarni qayta tiklaydi, shuning uchun ularni yana yoqamiz.
	 */
	private static void turnOnSingleplayer(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		UUID id = client.player.getUUID();
		float speed = speed();

		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(id);

			if (sp == null || sp.isCreative() || sp.isSpectator()) {
				return;
			}

			Abilities a = sp.getAbilities();

			if (!a.mayfly || a.getFlyingSpeed() != speed) {
				a.mayfly = true;
				a.setFlyingSpeed(speed);
				sp.onUpdateAbilities();
			}
		});
	}

	// ---------- Server (Aternos va boshqalar) ----------

	private static void turnOnMultiplayer(Minecraft client) {
		LocalPlayer player = client.player;

		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		// Server o'lim yoki dunyo almashganda qobiliyatlarni qaytaradi, shuning uchun har tikda yoqamiz.
		Abilities a = player.getAbilities();
		a.mayfly = true;
		a.setFlyingSpeed(speed());

		if (a.flying) {
			// Serverga "yerdaman" deb aytamiz: shunda uchib bo'lib qo'nganda
			// server yiqilish masofasini hisoblamaydi va jon kamaymaydi.
			client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true));
		}
	}

	// ---------- O'chirish ----------

	private static void turnOff(Minecraft client) {
		if (client.player == null) {
			return;
		}

		if (client.hasSingleplayerServer()) {
			IntegratedServer server = client.getSingleplayerServer();
			UUID id = client.player.getUUID();

			server.execute(() -> {
				ServerPlayer sp = server.getPlayerList().getPlayer(id);

				if (sp == null || sp.isCreative() || sp.isSpectator()) {
					return;
				}

				Abilities a = sp.getAbilities();
				a.mayfly = false;
				a.flying = false;
				a.setFlyingSpeed(BASE_SPEED);
				sp.resetFallDistance();
				sp.onUpdateAbilities();
			});
		} else {
			LocalPlayer player = client.player;

			if (player.isCreative() || player.isSpectator()) {
				return;
			}

			Abilities a = player.getAbilities();
			a.mayfly = false;
			a.flying = false;
			a.setFlyingSpeed(BASE_SPEED);
		}
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), true);
		}
	}
}
