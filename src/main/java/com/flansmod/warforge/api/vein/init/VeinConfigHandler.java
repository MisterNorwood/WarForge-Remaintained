package com.flansmod.warforge.api.vein.init;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import lombok.AllArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.naming.ConfigurationException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.flansmod.warforge.api.vein.init.VeinUtils.NULL_VEIN_ID;
import static com.flansmod.warforge.common.WarForgeMod.VEIN_HANDLER;

public class VeinConfigHandler {
    public record Tuple5<A, B, C, D, E>(A _1, B _2, C _3, D _4, E _5) {}

    public final static Path CONFIG_PATH = Paths.get("config/" + Tags.MODID + "/veins.toml");
    public final static Path LEGACY_CONFIG_PATH = Paths.get("config/" + Tags.MODID + "/veins.cfg");
    public static final List<String> EXAMPLE_TOML = Collections.unmodifiableList(Arrays.asList(
            "# It should be said that it is RECOMMENDED to BACKUP ANY vein config files with significant time invested into them; ",
            "# trying to update the file when auto-generated vein ids are present may cause data loss if a poorly timed error occurs.",
            "# Additionally, all comments will be removed when auto-generated vein ids are used.",
            "#",
            "# There must be a global iteration id [-32768, 32767] ('iteration'). This is stored alongside the vein id for discovered chunks",
            "# and will cause the saved vein id to be overridden if the iteration id's don't match between the stored and current value.",
            "#",
            "# There must be a global megachunk_length which is the side length [4, 180] of the regions in which the weight minimums are respected.",
            "# There is no guarantee of veins occurring at least once, but they are guaranteed floor(megachunk_length^2 * weight)",
            "#",
            "# Example vein definition format (TOML). Each vein is an entry in the [[veins]] array of tables:",
            "# - id: An auto-generated unique identifier [0, 8191] which allows for vein properties to change;",
            "#       OMIT this key entirely to have one assigned automatically.",
            "# - key: A unique translation key used for localization or identification.",
            "# - wealth: An integer (default 1) added to a faction's wealth for each claimed chunk holding this vein.",
            "# - quals: An inline table of quality overrides for this vein, with omitted qualities using the global default in cfg",
            "#       quals = { <Qual Name> = <float multiplier>, ... }",
            "# - dims: An array of inline tables of dimension weights, where:",
            "#       id: The dimension ResourceLocation (e.g. minecraft:overworld, minecraft:the_nether, minecraft:the_end, or a modded dim).",
            "#       weight: A float between 0.0 and 1.0 indicating the relative generation chance in that dimension.",
            "#       mult: A float between 0.0 and 1.0 which scales the yield of a vein based on the dimension; omission => 1.",
            "# - components: An array of inline tables of ore components for the vein, where:",
            "#       item: The item ID (e.g. minecraft:iron_ore) to generate in the vein.",
            "#       yield: How many of this item the vein yields when selected. [float w/ decimal value contributed to bonus chance]",
            "#       weights: An array of { id = <dimension ResourceLocation>, weight = <float 0..1> } tables (omitted dimensions assume a weight of 1.0)",
            "#       mults: An array of { id = <dimension ResourceLocation>, mult = <float >= 0.0> } tables (omitted dimensions use the vein dim multiplier)",
            "#     NOTE: If component weights are omitted or empty, all are assumed to have a weight of 1.0.",
            "#",
            "# All fields are mandatory unless otherwise specified.",
            "#",
            "# iteration = 0",
            "# megachunk_length = 32",
            "#",
            "# [[veins]]",
            "# key = \"warforge.veins.iron_mix\"",
            "# wealth = 1",
            "# quals = { RICH = 10.0, POOR = 0.1 }",
            "# dims = [",
            "#     { id = \"minecraft:the_nether\", weight = 0.5, mult = 2.0 },",
            "#     { id = \"minecraft:overworld\", weight = 0.4215 },",
            "#     { id = \"minecraft:the_end\", weight = 1.0 },",
            "# ]",
            "# components = [",
            "#     { item = \"minecraft:iron_ore\", yield = 2.0, weights = [ { id = \"minecraft:the_nether\", weight = 0.75 }, { id = \"minecraft:overworld\", weight = 1.0 } ] },",
            "#     { item = \"minecraft:coal_ore\", yield = 1.0, mults = [ { id = \"minecraft:the_nether\", mult = 4.0 } ] },",
            "# ]",
            "#..."
    ));

    // Default vein set written on a fresh install: a handful of simple, vanilla-friendly overworld
    // (plus one nether) ore veins whose weights leave roughly half of all chunks empty. Each vein has
    // an explicit id so the file is not rewritten (which would strip the comment block above).
    public static final List<String> DEFAULT_VEINS_TOML = Collections.unmodifiableList(Arrays.asList(
            "iteration = 0",
            "megachunk_length = 32",
            "",
            "[[veins]]",
            "id = 0",
            "key = \"warforge.veins.iron\"",
            "wealth = 1",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.15 } ]",
            "components = [",
            "    { item = \"minecraft:coal_ore\", yield = 3.0 },",
            "    { item = \"minecraft:iron_ore\", yield = 2.0 },",
            "]",
            "",
            "[[veins]]",
            "id = 1",
            "key = \"warforge.veins.copper\"",
            "wealth = 1",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.12 } ]",
            "components = [",
            "    { item = \"minecraft:copper_ore\", yield = 3.0 },",
            "]",
            "",
            "[[veins]]",
            "id = 2",
            "key = \"warforge.veins.redstone_lapis\"",
            "wealth = 1",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.10 } ]",
            "components = [",
            "    { item = \"minecraft:redstone_ore\", yield = 2.0 },",
            "    { item = \"minecraft:lapis_ore\", yield = 1.5 },",
            "]",
            "",
            "[[veins]]",
            "id = 3",
            "key = \"warforge.veins.gold\"",
            "wealth = 2",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.08 } ]",
            "components = [",
            "    { item = \"minecraft:gold_ore\", yield = 1.5 },",
            "]",
            "",
            "[[veins]]",
            "id = 4",
            "key = \"warforge.veins.gold_diamond\"",
            "wealth = 4",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.04 } ]",
            "components = [",
            "    { item = \"minecraft:gold_ore\", yield = 1.0 },",
            "    { item = \"minecraft:diamond_ore\", yield = 0.5 },",
            "]",
            "",
            "[[veins]]",
            "id = 5",
            "key = \"warforge.veins.emerald\"",
            "wealth = 3",
            "dims = [ { id = \"minecraft:overworld\", weight = 0.03 } ]",
            "components = [",
            "    { item = \"minecraft:emerald_ore\", yield = 0.75 },",
            "]",
            "",
            "[[veins]]",
            "id = 6",
            "key = \"warforge.veins.quartz\"",
            "wealth = 1",
            "dims = [ { id = \"minecraft:the_nether\", weight = 0.25 } ]",
            "components = [",
            "    { item = \"minecraft:nether_quartz_ore\", yield = 4.0 },",
            "]"
    ));

    public static void writeStubIfEmpty() throws IOException {
        migrateLegacyConfigIfNeeded();
        if (Files.notExists(CONFIG_PATH) || Files.size(CONFIG_PATH) == 0) {
            Files.createDirectories(CONFIG_PATH.getParent());
            List<String> stub = new ArrayList<>(EXAMPLE_TOML);
            stub.add("");
            stub.addAll(DEFAULT_VEINS_TOML);
            Files.write(
                    CONFIG_PATH,
                    stub,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        }
    }

    private static void migrateLegacyConfigIfNeeded() throws IOException {
        if (Files.exists(CONFIG_PATH) || Files.notExists(LEGACY_CONFIG_PATH)) {
            return;
        }

        Files.createDirectories(CONFIG_PATH.getParent());
        Files.move(LEGACY_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        WarForgeMod.LOGGER.info("Migrated legacy vein config from {} to {}", LEGACY_CONFIG_PATH, CONFIG_PATH);
    }

    // called after writeStub
    public static void loadVeins() throws ConfigurationException {
        // try to get the raw veins and write no veins on failure
        var veinData = parseGlobalVeinData();
        // we need to keep the global data to write to a file later
        if (veinData == null) {
            VEIN_HANDLER.populateVeinMap(null);
            return;
        }

        List<Config> allVeinData = veinData.getRight();
        if (allVeinData == null) {
            VEIN_HANDLER.populateVeinMap(null);
            return;
        }

        List<VeinEntry> entries = new ArrayList<>();
        Int2ObjectOpenHashMap<Tuple5<String, Object2FloatOpenHashMap<Quality>, Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight>, List<Component>, Integer>> noIdEntries = new Int2ObjectOpenHashMap<>();

        // we need this for further id processing
        short[] occupiedIds = new short[allVeinData.size()];
        int numIds = parseVeinEntries(allVeinData, entries, noIdEntries, occupiedIds);

        // get and order all occupied ids to try and place the new id's between them
        occupiedIds = Arrays.copyOf(occupiedIds, numIds);  // trim array down
        Arrays.sort(occupiedIds);
        if (occupiedIds.length == 0) { occupiedIds = new short[]{NULL_VEIN_ID}; }  // to be compatible with handler below, set to minimum to indicate all id's valid

        // setup idSpaces and offset to begin looping over veins without id's
        // first is the index of the integer to follow, second is number of open ids after this integer
        short[] idSpaces = new short[]{-1, occupiedIds[0]};
        short currIdOffset = 1;

        Int2ShortOpenHashMap posToId = new Int2ShortOpenHashMap(noIdEntries.size());
        for (var noIdEntry : noIdEntries.int2ObjectEntrySet()) {
            // relate the vein position in rawVeins to its new id
            int noIdEntryOGIndex = noIdEntry.getIntKey();
            var noIdEntryVal = noIdEntry.getValue();

            // if we used all available spaces, then we need to find the next space between taken id's
            if (currIdOffset > idSpaces[1]) {
                // get next id to start from and how many id's (spaces) we have until the next taken id
                idSpaces = locateNextSpace(occupiedIds, idSpaces[0] + 1);
                currIdOffset = 1;

                // if we didn't get any space we must be out of valid id's
                if (idSpaces[1] < 1) {
                    throw new ConfigurationException("Ran out of id spaces to assign to veins.");
                }
            }

            short id = (short) (occupiedIds[idSpaces[0]] + currIdOffset++);
            entries.add(new VeinEntry(id, noIdEntryVal._1(), noIdEntryVal._2(), noIdEntryVal._3(), noIdEntryVal._4(), noIdEntryVal._5()));
            posToId.put(noIdEntryOGIndex, id);
        }

        // try to write autogenerated ids
        if (noIdEntries.size() > 0) {
            try {
                // for each vein without an id, update its id in the config file
                var noIDEntriesIndices = noIdEntries.keySet();  // it's not saved, so store a copy here
                for (int noIDVeinIndex : noIDEntriesIndices) {
                    allVeinData.get(noIDVeinIndex).set("id", (int) posToId.get(noIDVeinIndex));
                }

                // wipe all previous text in the file and write the info string
                String infoString = String.join("\n", EXAMPLE_TOML) + "\n\n";
                try (FileWriter infoWriter = new FileWriter(CONFIG_PATH.toFile())) {
                    infoWriter.write(infoString);
                }

                // write the toml data to the file (appended after the comment block)
                try (FileWriter dataWriter = new FileWriter(CONFIG_PATH.toFile(), true)) {
                    new TomlWriter().write(veinData.getLeft(), dataWriter);
                }
            } catch (Exception exception) {
                WarForgeMod.LOGGER.atError().log("Could not write back to vein file; id's will not be updated.");
            }
        }

        VEIN_HANDLER.populateVeinMap(entries);
    }

    // attempts to read the global vein data, falling back to defaults for the VEIN_HANDLER if an error occurs
    private static Pair<CommentedConfig, List<Config>> parseGlobalVeinData() {
        CommentedConfig globalVeinData;  // used to rewrite to file
        List<Config> rawVeins;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {  // will throw if no file exists
            globalVeinData = new TomlParser().parse(reader);  // get a mapping of keys to objects

            // A stub config holds only the commented example block, so both globals parse as absent.
            // Treat missing globals as defaults instead of dereferencing null (no veins is valid).
            Number iterationRaw = globalVeinData.get("iteration");
            short iterationId = iterationRaw != null ? iterationRaw.shortValue() : 0;

            Number megachunkRaw = globalVeinData.get("megachunk_length");
            short megachunkLength = megachunkRaw != null ? megachunkRaw.shortValue() : 32;
            if (megachunkLength < 4 || megachunkLength > 180) {
                WarForgeMod.LOGGER.atError().log("Invalid megachunk length provided; defaulting to 32");
                megachunkLength = 32;
            }

            VEIN_HANDLER = new VeinUtils(iterationId, megachunkLength);
            rawVeins = globalVeinData.get("veins");
        } catch (Exception e) {
            WarForgeMod.LOGGER.error("Failed to parse veins: ", e);
            VEIN_HANDLER = new VeinUtils((short) 0, (short) 32);
            return null;
        }

        return Pair.of(globalVeinData, rawVeins);
    }

    // dimension ids are modern ResourceLocation strings (e.g. minecraft:the_nether).
    private static ResourceLocation parseDimId(Object raw) {
        return new ResourceLocation((String) raw);
    }

    // returns the number of occupiedIds found
    private static int parseVeinEntries(List<Config> rawVeins, List<VeinEntry> entries,
                                        Int2ObjectOpenHashMap<Tuple5<String, Object2FloatOpenHashMap<Quality>,
                                            Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight>, List<Component>, Integer>> noIdEntries,
                                        short[] occupiedIds) {
        int numIds = 0;
        int veinIndex = -1;
        // parse all veins
        for (Config veinData : rawVeins) {
            ++veinIndex;
            try {
                // try to get the id, or determine if it is invalid
                short absoluteId = NULL_VEIN_ID;  // start off with the null vein id
                try {
                    Number idNum = veinData.get("id");
                    short currId = NULL_VEIN_ID;
                    if (idNum != null) { currId = idNum.shortValue(); }
                    if (currId >= 0 && currId < 8192) { absoluteId = currId; }
                } catch (Exception castError) {
                    // the default state already indicates an error; we don't need to do any handling
                }

                // get key and try to get quality overrides, if there any
                String translationKey = veinData.get("key");
                Config qualsRaw = null;
                try {
                    qualsRaw = veinData.get("quals");
                } catch (Exception e) {
                    WarForgeMod.LOGGER.atDebug().log("Failed to get quality overrides for vein with key " + translationKey);
                }

                // try to extract qualities
                Object2FloatOpenHashMap<Quality> quals = null;  // null by default to indicate no overrides
                if (qualsRaw != null) {
                    quals = new Object2FloatOpenHashMap<>(Quality.values().length);
                    for (Quality qual : Quality.values()) {
                        if (!qualsRaw.contains(qual.toString())) { continue; }
                        quals.put(qual, ((Number) qualsRaw.get(qual.toString())).floatValue());
                    }
                }

                // get dim info
                List<Config> dimsRaw = veinData.get("dims");

                Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight> dims = new Object2ObjectOpenHashMap<>(dimsRaw.size());
                for (Config dimRaw : dimsRaw) {
                    float multiplier = 1;
                    if (dimRaw.contains("mult")) { multiplier = ((Number) dimRaw.get("mult")).floatValue(); }
                    ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                            parseDimId(dimRaw.get("id")));
                    dims.put(dimKey, new DimWeight(dimKey,
                            VeinUtils.percentToShort(((Number) dimRaw.get("weight")).floatValue()),
                            multiplier));
                }

                // read the component data
                List<Config> componentsRaw = veinData.get("components");
                List<Component> components = componentsRaw.stream()
                        .map(comp -> new Component(
                            comp.get("item"),
                            ((Number) comp.get("yield")).floatValue(),
                            Component.parseMapF2S(comp.get("weights")),
                            Component.parseFloatMap(comp.get("mults"))))
                        .collect(Collectors.toList());

                // relative wealth contributed by a claimed chunk holding this vein; defaults to 1
                Number wealthNum = veinData.get("wealth");
                int wealth = wealthNum != null ? wealthNum.intValue() : 1;

                if (absoluteId == NULL_VEIN_ID) {
                    noIdEntries.put(veinIndex, new Tuple5<>(translationKey, quals, dims, components, wealth));
                    continue;
                }

                entries.add(new VeinEntry(absoluteId, translationKey, quals, dims, components, wealth));
                occupiedIds[numIds++] = absoluteId;  // we want positive numbers to assign id's
            } catch (ClassCastException e) {
                WarForgeMod.LOGGER.error("Failed to parse vein: ", e);
            }
        }

        return numIds;
    }

    // returns the index of the soonest integer which has ints between itself and the next integer after it,
    // followed by the number of open indices between the two. Will return INT_MAX at the end of the array
    public static short[] locateNextSpace(short[] sparseInts, int currIndex) {
        for (; currIndex < sparseInts.length - 1; ++currIndex) {
            // if there is space between this int and the next, this is our target
            if (sparseInts[currIndex] < sparseInts[currIndex + 1] - 1) {
                return new short[]{(short) currIndex, (short) (sparseInts[currIndex + 1] - sparseInts[currIndex] - 1)};
            }
        }

        // if we got to the end of the array, then just start from there
        return new short[]{(short) currIndex, (short) (0x00_00_1F_FF - sparseInts[currIndex])};
    }

    public static class VeinEntry {
        final public short id;
        final public String translationKey;
        public float[] qualMults;
        public final byte qualOverrideCount;

        final public Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight> dimWeights;
        final public List<Component> components;
        final public int wealth;

        public VeinEntry(short id, String translationKey, Object2FloatOpenHashMap<Quality> qualMults, Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight> dimWeights, List<Component> components, int wealth) {
            this.id = id;
            this.translationKey = translationKey;
            this.dimWeights = dimWeights;
            this.components = components;
            this.wealth = wealth;
            if (qualMults == null) {
                this.qualMults = null;
                qualOverrideCount = 0;
                return;
            }

            this.qualMults = new float[Quality.values().length];
            byte qualOverrideCount = 0;
            for (Quality qual : Quality.values()) {
                if (qualMults.containsKey(qual)) {
                    this.qualMults[qual.ordinal()] = qualMults.getFloat(qual);
                    ++qualOverrideCount;
                } else {
                    this.qualMults[qual.ordinal()] = -1f;
                }
            }

            this.qualOverrideCount = qualOverrideCount;
        }

        public FriendlyByteBuf serialize() {
            // collect all the components together to know the size
            final int[] compBufBytes = {0};
            ArrayList<FriendlyByteBuf> compBufs = new ArrayList<>(components.size());
            components.forEach(comp -> {
                FriendlyByteBuf compSerialized = comp.serialize();
                compBufBytes[0] += compSerialized.readableBytes();
                compBufs.add(compSerialized);
            });

            FriendlyByteBuf entryByteBuf = new FriendlyByteBuf(Unpooled.buffer());

            // write id and translation key
            entryByteBuf.writeShort(id);
            entryByteBuf.writeUtf(translationKey);
            entryByteBuf.writeInt(wealth);

            // store qual overrides
            entryByteBuf.writeByte(qualOverrideCount);
            if (qualMults != null) {
                for (byte qualOrd = 0; qualOrd < qualMults.length; ++qualOrd) {
                    float qualMult = qualMults[qualOrd];
                    if (qualMult == -1) { continue; }  // -1 means we want to use the default
                    entryByteBuf.writeByte(qualOrd);
                    entryByteBuf.writeFloat(qualMult);
                }
            }

            // store dim weights
            entryByteBuf.writeInt(dimWeights.size());
            dimWeights.forEach((dimKey, dimWeight) -> dimWeight.serialize(entryByteBuf));

            // store component details
            entryByteBuf.writeInt(components.size());
            compBufs.forEach(entryByteBuf::writeBytes);

            return entryByteBuf;
        }

        public static VeinEntry deserialize(FriendlyByteBuf buf) {
            short id = buf.readShort();
            String translationKey = buf.readUtf();
            int wealth = buf.readInt();

            // prepare to read quality overrides
            int numQualOverrides = buf.readByte();
            Object2FloatOpenHashMap<Quality> qualMappings = null;
            if (numQualOverrides > 0) { qualMappings = new Object2FloatOpenHashMap<>(numQualOverrides); }

            // read in the quality overrides
            for (; numQualOverrides > 0; --numQualOverrides) {
                qualMappings.put(Quality.getQuality(buf.readByte()), buf.readFloat());
            }

            // read dims
            int numDims = buf.readInt();
            Object2ObjectOpenHashMap<ResourceKey<Level>, DimWeight> dimWeights = new Object2ObjectOpenHashMap<>(numDims);
            for (int i = 0; i < numDims; ++i) {
                DimWeight dw = DimWeight.deserialize(buf);
                dimWeights.put(dw.dim, dw);
            }

            // read components
            int numComps = buf.readInt();
            List<Component> comps = new ArrayList<>(numComps);
            for (int i = 0; i < numComps; ++i) {
                comps.add(Component.deserialize(buf));
            }

            return new VeinEntry(id, translationKey, qualMappings, dimWeights, comps, wealth);
        }
    }

    @AllArgsConstructor
    public static class DimWeight {
        final public ResourceKey<Level> dim;
        final public short weight;
        final public float multiplier;

        public void serialize(FriendlyByteBuf buf) {
            buf.writeUtf(dim.location().toString());
            buf.writeShort(weight);
            buf.writeFloat(multiplier);
        }

        public static DimWeight deserialize(FriendlyByteBuf buf) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buf.readUtf()));
            return new DimWeight(dim, buf.readShort(), buf.readFloat());
        }
    }

    @AllArgsConstructor
    public static class Component {
        final public String item;
        final public float yield;
        final public Object2ShortOpenHashMap<ResourceKey<Level>> weights;
        final public Object2FloatOpenHashMap<ResourceKey<Level>> multipliers;

        // mults entries are { id = <dimension>, mult = <float> } tables
        public static Object2FloatOpenHashMap<ResourceKey<Level>> parseFloatMap(List<? extends Config> multEntries) {
            Object2FloatOpenHashMap<ResourceKey<Level>> result = new Object2FloatOpenHashMap<>();
            if (multEntries == null) { return result; }
            for (Config dimEntry : multEntries) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, parseDimId(dimEntry.get("id")));
                result.put(key, ((Number) dimEntry.get("mult")).floatValue());
            }
            return result;
        }

        // weights entries are { id = <dimension>, weight = <float> } tables
        public static Object2ShortOpenHashMap<ResourceKey<Level>> parseMapF2S(List<? extends Config> weightEntries) {
            Object2ShortOpenHashMap<ResourceKey<Level>> result = new Object2ShortOpenHashMap<>();
            if (weightEntries == null) { return result; }
            for (Config dimEntry : weightEntries) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, parseDimId(dimEntry.get("id")));
                result.put(key, VeinUtils.percentToShort(((Number) dimEntry.get("weight")).floatValue()));
            }
            return result;
        }

        public FriendlyByteBuf serialize() {
            FriendlyByteBuf serialData = new FriendlyByteBuf(Unpooled.buffer());
            serialData.writeUtf(item);
            serialData.writeFloat(yield);

            serialData.writeInt(weights.size());
            for (var entry : weights.object2ShortEntrySet()) {
                serialData.writeUtf(entry.getKey().location().toString());
                serialData.writeShort(entry.getShortValue());
            }

            serialData.writeInt(multipliers.size());
            for (var entry : multipliers.object2FloatEntrySet()) {
                serialData.writeUtf(entry.getKey().location().toString());
                serialData.writeFloat(entry.getFloatValue());
            }

            return serialData;
        }

        public static Component deserialize(FriendlyByteBuf buf) {
            String item = buf.readUtf();
            float yield = buf.readFloat();

            // read weights which are stored first
            int numWeights = buf.readInt();
            Object2ShortOpenHashMap<ResourceKey<Level>> weights = new Object2ShortOpenHashMap<>(numWeights);
            for (int i = 0; i < numWeights; ++i) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buf.readUtf()));
                weights.put(key, buf.readShort());
            }

            // read multipliers which are stored next
            int numMults = buf.readInt();
            Object2FloatOpenHashMap<ResourceKey<Level>> multipliers = new Object2FloatOpenHashMap<>(numMults);
            for (int i = 0; i < numMults; ++i) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(buf.readUtf()));
                multipliers.put(key, buf.readFloat());
            }

            return new Component(item, yield, weights, multipliers);
        }
    }

}
