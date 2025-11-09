package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProximityChatMod implements ModInitializer {
    // Глобальный радиус для всего сервера
    private static double globalRadiusSquared = 2500.0; // 50 * 50 по умолчанию
    private static final String CONFIG_FILE = "config/proximitychat_radius.txt";

    @Override
    public void onInitialize() {
        System.out.println("Proximity Chat Mod initialized!");

        // Загружаем сохраненный радиус при запуске
        loadRadiusFromConfig();

        // Регистрируем команду
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("proximitychat")
                    .requires(source -> source.hasPermissionLevel(2)) // Только для операторов
                    .then(net.minecraft.server.command.CommandManager.literal("setradius")
                        .then(net.minecraft.server.command.CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 1000.0))
                            .executes(this::executeSetRadius)
                        )
                    )
                    .then(net.minecraft.server.command.CommandManager.literal("getradius")
                        .executes(this::executeGetRadius)
                    )
            );
        });

        // Обработчик чата
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            boolean foundSomeone = false;

            // Получаем всех игроков через менеджер игроков
            for (ServerPlayerEntity player : sender.getServer().getPlayerManager().getPlayerList()) {
                if (player == sender) continue;

                if (isInRange(sender, player, globalRadiusSquared)) {
                    foundSomeone = true;
                    // Отправляем сообщение этому игроку
                    player.sendMessage(Text.literal("<" + sender.getEntityName() + "> " + message.getContent().getString()), false);
                }
            }

            if (foundSomeone) {
                // Отправляем сообщение самому отправителю
                sender.sendMessage(Text.literal("<" + sender.getEntityName() + "> " + message.getContent().getString()), false);
            } else {
                sender.sendMessage(Text.literal("§cВас никто не услышал"), false);
            }

            return false; // Всегда отменяем стандартное сообщение
        });
    }

    private int executeSetRadius(CommandContext<ServerCommandSource> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        ServerCommandSource source = context.getSource();

        globalRadiusSquared = radius * radius;

        // Сохраняем радиус в конфиг
        saveRadiusToConfig(radius);

        // Сообщаем всем игрокам об изменении радиуса
        String message = "§aГлобальный радиус слышимости чата установлен на " + radius + " блоков";
        source.getServer().getPlayerManager().broadcast(Text.literal(message), false);

        return 1; // Успех
    }

    private int executeGetRadius(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        double radius = Math.sqrt(globalRadiusSquared);

        source.sendMessage(Text.literal("§eТекущий радиус слышимости чата: " + radius + " блоков"));
        return 1;
    }

    private boolean isInRange(ServerPlayerEntity p1, ServerPlayerEntity p2, double rangeSquared) {
        double dx = p1.getX() - p2.getX();
        double dy = p1.getY() - p2.getY();
        double dz = p1.getZ() - p2.getZ();
        return (dx * dx + dy * dy + dz * dz) <= rangeSquared;
    }

    // Загрузка радиуса из файла конфигурации
    private void loadRadiusFromConfig() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.exists(configPath)) {
                String content = new String(Files.readAllBytes(configPath)).trim();
                double radius = Double.parseDouble(content);
                globalRadiusSquared = radius * radius;
                System.out.println("Загружен радиус слышимости из конфига: " + radius + " блоков");
            } else {
                // Создаем конфиг с радиусом по умолчанию
                saveRadiusToConfig(50.0);
                System.out.println("Создан конфиг с радиусом по умолчанию: 50 блоков");
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки конфига: " + e.getMessage());
            // Используем радиус по умолчанию при ошибке
            globalRadiusSquared = 2500.0;
        }
    }

    // Сохранение радиуса в файл конфигурации
    private void saveRadiusToConfig(double radius) {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            Path configDir = configPath.getParent();

            // Создаем директорию config если не существует
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // Записываем радиус в файл
            Files.write(configPath, String.valueOf(radius).getBytes());
            System.out.println("Сохранен радиус в конфиг: " + radius + " блоков");
        } catch (Exception e) {
            System.err.println("Ошибка сохранения конфига: " + e.getMessage());
        }
    }
}
