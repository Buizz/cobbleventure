package dev.buizz.cobbleventure.adventure.research;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Research prices are independent of crafting recipes. Mega definitions follow active data packs. */
public final class ResearchCatalog {
    private ResearchCatalog() {}
    static JsonObject load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(ResourceLocation.parse("cobbleventure_adventure:research/settings.json"))
            .orElseThrow(() -> new IllegalArgumentException("Research settings unavailable"));
        try (var reader = resource.openAsReader()) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException("Research settings unavailable", ex); }
    }
    static int cost(JsonObject config, String key) {
        int result = config.getAsJsonObject("costs").get(key).getAsInt();
        if (result < 0 || result > 1_000_000) throw new IllegalArgumentException("Invalid research cost");
        return result;
    }
    static String material(JsonObject config, String key) { return config.getAsJsonObject("materials").get(key).getAsString(); }
    public static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    static ItemStack item(String id) {
        var key = ResourceLocation.tryParse(id);
        return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
    static boolean gmax(Pokemon pokemon) {
        String base = normalize(pokemon.showdownId());
        return pokemon.getSpecies().getForms().stream().anyMatch(form ->
            normalize(form.showdownId()).equals(base + "gmax")
                || (normalize(form.getName()).equals("gmax") && pokemon.getForm() == pokemon.getSpecies().getStandardForm()));
    }
    static boolean dynamax(JsonObject config, Pokemon pokemon) {
        for (var name : config.getAsJsonArray("dynamax_excluded"))
            if (normalize(name.getAsString()).equals(normalize(pokemon.getSpecies().getName()))) return false;
        return true;
    }
    record Option(String item, String ingredient, int count, String extra, int extraCount, long money) {
        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("item", item); value.addProperty("ingredient", ingredient); value.addProperty("count", count);
            value.addProperty("extra", extra); value.addProperty("extra_count", extraCount); value.addProperty("money", money);
            return value;
        }
    }
    static List<Option> options(MinecraftServer server, JsonObject config, String kind, Pokemon pokemon) {
        List<Option> result = new ArrayList<>();
        if (kind.equals("mega")) {
            server.getResourceManager().listResources("mega_showdown/mega", id -> id.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        var data = JsonParser.parseReader(reader).getAsJsonObject();
                        boolean matches = false;
                        for (var candidate : data.getAsJsonArray("pokemons"))
                            if (normalize(candidate.getAsString()).equals(normalize(pokemon.showdownId()))) matches = true;
                        if (!matches) return;
                        String output = id.getNamespace() + ":" + id.getPath().substring("mega_showdown/mega/".length()).replace(".json", "");
                        if (!item(output).isEmpty()) result.add(new Option(output, material(config, "mega"), 1, "", 0, cost(config, "mega_money")));
                    } catch (java.io.IOException | IllegalStateException ex) {
                        throw new IllegalArgumentException("Invalid mega definition " + id, ex);
                    }
                });
        } else if (kind.equals("z_move")) {
            Set<String> types = new HashSet<>(), moves = new HashSet<>();
            for (var move : pokemon.getMoveSet().getMoves()) {
                types.add(move.getType().getName().toLowerCase(Locale.ROOT));
                moves.add(normalize(move.getName()));
            }
            for (var element : config.getAsJsonArray("z_crystals")) {
                var row = element.getAsJsonObject();
                String type = row.get("type").getAsString().toLowerCase(Locale.ROOT);
                boolean exclusive = !row.get("move").getAsString().isBlank();
                List<String> users = new ArrayList<>();
                row.getAsJsonArray("users").forEach(user -> users.add(user.getAsString()));
                if (!ResearchPolicy.zCompatible(type, row.get("move").getAsString(), users, pokemon.showdownId(), moves, types)) continue;
                String output = row.get("item").getAsString(), gem = "cobblemon:" + type + "_gem";
                if (!item(output).isEmpty() && !item(gem).isEmpty())
                    result.add(new Option(output, material(config, "blank_z"), 1, gem,
                        cost(config, exclusive ? "exclusive_z_gems" : "z_gems"), 0));
            }
        }
        result.sort(Comparator.comparing(Option::item));
        return result;
    }
}
