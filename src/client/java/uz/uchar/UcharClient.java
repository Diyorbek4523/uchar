package uz.uchar;

import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Bir kishilik survival dunyoda uchish.
 * O'yinning o'zidagi (creative'dagi kabi) uchish qobiliyatini yoqadi:
 * Space tugmasini ikki marta bosib uchasiz, Shift bilan pastga tushasiz.
 * Uchish yoqilgan paytda balanddan yiqilsangiz ham jon kamaymaydi.
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

	private static void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			if (client.player == null) {
				continue;
			}

			if (!client.hasSingleplayerServer()) {
				message(client, "§cUchish faqat bir kishilik dunyoda ishlaydi");
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

		// Dunyodan chiqqanda yoki serverga ulanganda avtomatik o'chadi.
		if (client.player == null || !client.hasSingleplayerServer()) {
			enabled = false;
			return;
		}

		turnOn(client);
	}

	/**
	 * Har tikda tekshiradi: o'lgandan keyin yoki boshqa dunyoga (Nether, End) o'tganda
	 * o'yin qobiliyatlarni qayta tiklaydi, shuning uchun ularni yana yoqamiz.
	 */
	private static void turnOn(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();
		UUID id = client.player.getUUID();
		float speed = BASE_SPEED * SPEEDS[speedIndex];

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

	private static void turnOff(Minecraft client) {
		IntegratedServer server = client.getSingleplayerServer();

		if (server == null || client.player == null) {
			return;
		}

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
	}

	private static void message(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(text), true);
		}
	}
}
