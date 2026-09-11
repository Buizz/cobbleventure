package dev.buizz.cobbleventure.themeblocks;

import dev.buizz.cobbleventure.content.ContentCatalog;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CobbleventureThemeBlocks.MOD_ID)
public final class CobbleventureThemeBlocks {
    public static final String MOD_ID = "cobbleventure_theme_blocks";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final List<DeferredItem<BlockItem>> CREATIVE_ITEMS = new ArrayList<>();

    public CobbleventureThemeBlocks(IEventBus modBus) {
        for (var entry : ContentCatalog.read("data/cobbleventure/catalogs/theme-blocks.json").getAsJsonArray("blocks")) {
            JsonObject definition = entry.getAsJsonObject();
            String id = definition.get("id").getAsString();
            DeferredBlock<Block> block = BLOCKS.register(id, () -> createBlock(definition));
            CREATIVE_ITEMS.add(ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CobbleventureThemeBlocks::addCreativeTabItems);
    }

    private static Block createBlock(JsonObject definition) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
            .mapColor(mapColor(definition.get("map_color").getAsString()))
            .strength(definition.get("hardness").getAsFloat(), definition.get("resistance").getAsFloat())
            .sound(sound(definition.get("sound").getAsString()))
            .pushReaction(PushReaction.valueOf(definition.get("push_reaction").getAsString()))
            .lightLevel(state -> definition.get("light").getAsInt());
        if (definition.get("requires_tool").getAsBoolean()) properties.requiresCorrectToolForDrops();
        if (!definition.get("occlusion").getAsBoolean()) properties.noOcclusion();
        return switch (definition.get("behavior").getAsString()) {
            case "stone" -> new Block(properties);
            case "directional" -> new DirectionalStoneBlock(properties);
            case "fixed_directional" -> new FixedDirectionalFloorBlock(properties);
            case "grave" -> new PokemonTowerGraveBlock(properties);
            case "double_display_case" -> new DoubleDisplayCaseBlock(properties);
            case "double_glass_counter" -> new DoubleGlassDisplayCounterBlock(properties);
            case "rocket_machine_one" -> new RocketBaseMachineOneBlock(properties);
            case "rocket_machine_two" -> new RocketBaseMachineTwoBlock(properties);
            case "rocket_machine_three" -> new RocketBaseMachineThreeBlock(properties);
            case "research_device" -> new ResearchDeviceBlock(properties);
            case "bookshelf" -> new WideTallBookshelfBlock(properties);
            case "wide_furniture" -> new WideTallFurnitureBlock(properties);
            case "large_bed" -> new LargeBedBlock(properties);
            case "glow_window" -> new GlowWindowBlock(properties);
            case "double_glow_window" -> new DoubleGlowWindowBlock(properties);
            default -> throw new IllegalArgumentException("Unknown theme block behavior: " + definition);
        };
    }

    private static MapColor mapColor(String name) {
        return switch (name) {
            case "COLOR_BLUE" -> MapColor.COLOR_BLUE;
            case "COLOR_GREEN" -> MapColor.COLOR_GREEN;
            case "COLOR_LIGHT_BLUE" -> MapColor.COLOR_LIGHT_BLUE;
            case "COLOR_LIGHT_GRAY" -> MapColor.COLOR_LIGHT_GRAY;
            case "COLOR_LIGHT_GREEN" -> MapColor.COLOR_LIGHT_GREEN;
            case "COLOR_ORANGE" -> MapColor.COLOR_ORANGE;
            case "COLOR_PURPLE" -> MapColor.COLOR_PURPLE;
            case "COLOR_YELLOW" -> MapColor.COLOR_YELLOW;
            case "GOLD" -> MapColor.GOLD;
            case "METAL" -> MapColor.METAL;
            case "SAND" -> MapColor.SAND;
            case "TERRACOTTA_YELLOW" -> MapColor.TERRACOTTA_YELLOW;
            default -> throw new IllegalArgumentException("Unknown map_color: " + name);
        };
    }

    private static SoundType sound(String name) {
        return switch (name) {
            case "GLASS" -> SoundType.GLASS;
            case "METAL" -> SoundType.METAL;
            case "STONE" -> SoundType.STONE;
            case "WOOD" -> SoundType.WOOD;
            default -> throw new IllegalArgumentException("Unknown sound: " + name);
        };
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) CREATIVE_ITEMS.forEach(event::accept);
    }
}
