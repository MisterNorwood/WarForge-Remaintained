package com.flansmod.warforge.api.vein.init;

import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.common.network.PacketBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import lombok.AllArgsConstructor;
import org.yaml.snakeyaml.Yaml;
import scala.Tuple3;

import javax.naming.ConfigurationException;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
    public static final class Tuple5<A, B, C, D, E> {
        public final A _1;
        public final B _2;
        public final C _3;
        public final D _4;
        public final E _5;
        public Tuple5(A a, B b, C c, D d, E e) { _1 = a; _2 = b; _3 = c; _4 = d; _5 = e; }
    }

    static Yaml yaml;

    static {
        yaml = new Yaml();
    }

    public final static Path CONFIG_PATH = Paths.get("config/" + Tags.MODID + "/veins.yml");
    public final static Path LEGACY_CONFIG_PATH = Paths.get("config/" + Tags.MODID + "/veins.cfg");
    public static final List<String> EXAMPLE_YAML = Collections.unmodifiableList(Arrays.asList(
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
            "# Example vein definition format.",
            "# Each vein entry must contain:",
            "# - id: An auto-generated unique identifier [0, 8191] which allows for vein properties to change; leave as '~' to auto-gen.",
            "# - key: A unique translation key used for localization or identification.",
            "# - wealth: An integer (default 1) added to a faction's wealth for each claimed chunk holding this vein.",
            "# - quals: A list of quality overrides for this vein, with ommitted qualities using the global default in cfg",
            "#     - <Qual Name>: <float multiplier>",
            "# - dims: A list of dimension weights, where:",
            "#     - id: The dimension ID (e.g. -1 for Nether, 0 for Overworld, 1 for End or custom).",
            "#       weight: A float between 0.0 and 1.0 indicating the relative generation chance in that dimension.",
            "#       mult: A float between 0.0 and 1.0 which scales the yield of a vein based on the dimension; omission => 1.",
            "# - components: A list of ore components for the vein, where:",
            "#     - item: The item ID (e.g. minecraft:iron_ore) to generate in the vein.",
            "#       yield: How many of this item the vein yields when selected. [float w/ decimal value contributed to bonus chance]",
            "#       weights: A list of chances to appear on any given harvest of this vein for this component in each dim (omitted dimensions assume a weight of 1.0)",
            "#          - id [dimension id as int] : weight [chance to appear as float between 0 and 1]",
            "#       mults: A list of multipliers for this comp in each dim (omitted dimensions use the vein dim multiplier)",
            "#          - id [dimension id as int] : mult [yield multiplier as float >= 0.0]",
            "#     NOTE: If component weights are omitted or empty, all are assumed to have a weight of 1.0.",
            "#",
            "# All fields are mandatory unless otherwise specified.",
            "# The number of dimension weights must match the number of dimension IDs.",
            "# The number of component weights must match the number of components.",
            "#",
            "# iteration: 0",
            "# megachunk_length: 32",
            "# veins:",
            "#   - id: ~",
            "#     key: warforge.veins.iron_mix",
            "#     wealth: 1",
            "#     quals:",
            "#       - RICH: 10",
            "#         POOR: 0.1",
            "#     dims:",
            "#       - id: -1",
            "#         weight: 0.5",
            "#         mult: 2",
            "#       - id: 0",
            "#         weight: 0.4215",
            "#       - id: 1",
            "#         weight: 1.0",
            "#     components:",
            "#       - item: minecraft:iron_ore",
            "#         yield: 2",
            "#         weights: ",
            "#           - -1 : 0.75",
            "#           - 0 : 1.0",
            "#           [implicit 1.0 weight for dim with id not listed (1, 2, etc)]",
            "#       - item: minecraft:coal_ore",
            "#         yield: 1",
            "#         mults: ",
            "#           - -1: 4",
            "#           [implicit 1.0 multiplier for the remaining dimension(s)]",
            "#    - id: ~",
            "#..."
    ));

    public static void writeStubIfEmpty() throws IOException {
        migrateLegacyConfigIfNeeded();
        if (Files.notExists(CONFIG_PATH) || Files.size(CONFIG_PATH) == 0) {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(
                    CONFIG_PATH,
                    EXAMPLE_YAML,
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

        List<LinkedHashMap<String, Object>> allVeinData = veinData.getRight();
        if (allVeinData == null) {
            VEIN_HANDLER.populateVeinMap(null);
            return;
        }

        List<VeinEntry> entries = new ArrayList<>();
        Int2ObjectOpenHashMap<Tuple5<String, Object2FloatOpenHashMap<Quality>, Int2ObjectOpenHashMap<DimWeight>, List<Component>, Integer>> noIdEntries = new Int2ObjectOpenHashMap<>();

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
            entries.add(new VeinEntry(id, noIdEntryVal._1, noIdEntryVal._2, noIdEntryVal._3, noIdEntryVal._4, noIdEntryVal._5));
            posToId.put(noIdEntryOGIndex, id);
        }

        // try to write autogenerated ids
        if (noIdEntries.size() > 0) {
            try {
                // for each vein without an id, update its id in the config file
                var noIDEntriesIndices = noIdEntries.keySet();  // it's not saved, so store a copy here
                for (int noIDVeinIndex : noIDEntriesIndices) {
                    allVeinData.get(noIDVeinIndex).put("id", posToId.get(noIDVeinIndex));
                }

                // wipe all previous text in the file and write the info string
                String infoString = String.join("\n", EXAMPLE_YAML) + "\n\n";
                FileWriter infoWriter = new FileWriter(CONFIG_PATH.toFile());
                infoWriter.write(infoString);
                infoWriter.close();

                // write the yaml data to the file
                yaml.dump(veinData.getLeft(), new FileWriter(CONFIG_PATH.toFile(), true));  // write data
            } catch (Exception exception) {
                WarForgeMod.LOGGER.atError().log("Could not write back to vein file; id's will not be updated.");
            }
        }

        VEIN_HANDLER.populateVeinMap(entries);
    }

    // attempts to read the global vein data, falling back to defaults for the VEIN_HANDLER if an error occurs
    private static Pair<LinkedHashMap<String, Object>, List<LinkedHashMap<String, Object>>> parseGlobalVeinData() {
        LinkedHashMap<String, Object> globalVeinData;  // used to rewrite to file
        List<LinkedHashMap<String, Object>> rawVeins;
        try {
            InputStream inputStream = new FileInputStream(CONFIG_PATH.toFile());  // will throw if no file exists
            globalVeinData = yaml.load(inputStream);  // get a mapping of keys to objects
            short iterationId = ((Number) globalVeinData.get("iteration")).shortValue();

            short megachunkLength = ((Number) globalVeinData.get("megachunk_length")).shortValue();
            if (megachunkLength < 4 || megachunkLength > 180) {
                WarForgeMod.LOGGER.atError().log("Invalid megachunk length provided; defaulting to 32");
                megachunkLength = 32;
            }

            VEIN_HANDLER = new VeinUtils(iterationId, megachunkLength);
            rawVeins = (List<LinkedHashMap<String, Object>>) globalVeinData.get("veins");
        } catch (Exception e) {
            WarForgeMod.LOGGER.error("Failed to parse veins: ", e);
            VEIN_HANDLER = new VeinUtils((short) 0, (short) 32);
            return null;
        }

        return Pair.of(globalVeinData, rawVeins);
    }

    // returns the number of occupiedIds found
    private static int parseVeinEntries(List<LinkedHashMap<String, Object>> rawVeins, List<VeinEntry> entries,
                                        Int2ObjectOpenHashMap<Tuple5<String, Object2FloatOpenHashMap<Quality>,
                                            Int2ObjectOpenHashMap<DimWeight>, List<Component>, Integer>> noIdEntries,
                                        short[] occupiedIds) {
        int numIds = 0;
        int veinIndex = -1;
        // parse all veins
        for (LinkedHashMap<String, Object> veinData : rawVeins) {
            ++veinIndex;
            try {
                // try to get the id, or determine if it is invalid
                short absoluteId = NULL_VEIN_ID;  // start off with the null vein id
                try {
                    Number idNum = (Number) veinData.get("id");
                    short currId = NULL_VEIN_ID;
                    if (idNum != null) { currId = idNum.shortValue(); }
                    if (currId >= 0 && currId < 8192) { absoluteId = currId; }
                } catch (Exception castError) {
                    // the default state already indicates an error; we don't need to do any handling
                }

                // get key and try to get quality overrides, if there any
                String translationKey = (String) veinData.get("key");
                Map<String, Object> qualsRaw = null;
                try {
                    qualsRaw = ((List<Map<String, Object>>) veinData.get("quals")).get(0);
                } catch (Exception e) {
                    WarForgeMod.LOGGER.atDebug().log("Failed to get quality overrides for vein with key " + translationKey);
                }

                // try to extract qualities
                Object2FloatOpenHashMap<Quality> quals = null;  // null by default to indicate no overrides
                if (qualsRaw != null) {
                    quals = new Object2FloatOpenHashMap<>(qualsRaw.size());
                    for (Quality qual : Quality.values()) {
                        if (!qualsRaw.containsKey(qual.toString())) { continue; }
                        quals.put(qual, ((Number) qualsRaw.get(qual.toString())).floatValue());
                    }
                }

                // get dim info
                List<Map<String, Object>> dimsRaw = (List<Map<String, Object>>) veinData.get("dims");

                // lack of dash on weight indicates singular id, weight object in .cfg yml file
                Int2ObjectOpenHashMap<DimWeight> dims = new Int2ObjectOpenHashMap(dimsRaw.stream().map(dim -> {
                        float multiplier = 1;
                        if (dim.containsKey("mult")) { multiplier = ((Number) dim.get("mult")).floatValue(); }
                        return new DimWeight(
                                ((Number) dim.get("id")).intValue(),
                                VeinUtils.percentToShort(((Number) dim.get("weight")).floatValue()),
                                multiplier);
                }).collect(Collectors.toMap(dimWeight -> dimWeight.id, dimWeight -> dimWeight)) );

                // read the component data
                List<Map<String, Object>> componentsRaw = (List<Map<String, Object>>) veinData.get("components");
                List<Component> components = componentsRaw.stream()
                        .map(comp -> new Component(
                            (String) comp.get("item"),
                            ((Number) comp.get("yield")).floatValue(),
                            Component.parseMapF2S((List<Map<Object, Object>>) comp.get("weights")),
                            Component.parseFloatMap((List<Map<Object, Object>>) comp.get("mults"))))
                        .collect(Collectors.toList());

                Object wealthObj = veinData.get("wealth");
                int wealth = wealthObj instanceof Number ? ((Number) wealthObj).intValue() : 1;

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

        final public Int2ObjectOpenHashMap<DimWeight> dimWeights;
        final public List<Component> components;
        final public int wealth;

        public VeinEntry(short id, String translationKey, Object2FloatOpenHashMap<Quality> qualMults, Int2ObjectOpenHashMap<DimWeight> dimWeights, List<Component> components, int wealth) {
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

        public ByteBuf serialize() {
            // collect all the components together to know the size
            final int[] compBufBytes = {0};  // this way we make the byte buf with the exact size needed
            ArrayList<ByteBuf> compBufs = new ArrayList<>(components.size());
            components.forEach(comp -> {
                ByteBuf compSerialized = comp.serialize();
                compBufBytes[0] += compSerialized.readableBytes();
                compBufs.add(compSerialized);
            });

            // calculate size ahead of time
            ByteBuf entryByteBuf = Unpooled.directBuffer(2 + (2 + translationKey.length()) + 4 +
                    (1 + 5 * qualOverrideCount) + (DimWeight.byteCount * dimWeights.size()) + compBufBytes[0]);

            // write translation key
            entryByteBuf.writeShort(id);
            PacketBase.writeUTF(entryByteBuf, translationKey);
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
            dimWeights.forEach((dimId, dimWeight) -> entryByteBuf.writeInt(dimId).writeBytes(dimWeight.serialize()));

            // store component details
            entryByteBuf.writeInt(components.size());
            compBufs.forEach(entryByteBuf::writeBytes);

            return entryByteBuf;
        }

        public static VeinEntry deserialize(ByteBuf buf) {
            short id = buf.readShort();
            String translationKey = PacketBase.readUTF(buf);
            int wealth = buf.readInt();

            // prepare to read quality overrides
            int numQualOverrides = buf.readByte();
            Object2FloatOpenHashMap<Quality> qualMappings = null;
            if (numQualOverrides > 0) { qualMappings = new Object2FloatOpenHashMap<>(numQualOverrides); }

            // read in the quality overrides
            for (; numQualOverrides > 0; --numQualOverrides) {
                qualMappings.put(Quality.getQuality(buf.readByte()), buf.readFloat());
            }

            // read dims first
            int numDims = buf.readInt();
            Int2ObjectOpenHashMap<DimWeight> dimWeights = new Int2ObjectOpenHashMap<>(numDims);
            for (int i = 0; i < numDims; ++i) {
                dimWeights.put(buf.readInt(), DimWeight.deserialize(buf));
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
        final public int id;
        final public short weight;
        final public float multiplier;
        public static final int byteCount = 10;

        public ByteBuf serialize() {
            ByteBuf result = Unpooled.directBuffer(byteCount);
            result.writeInt(id);
            result.writeShort(weight);
            result.writeFloat(multiplier);
            return result;
        }

        public static DimWeight deserialize(ByteBuf buf) {
            return new DimWeight(buf.readInt(), buf.readShort(), buf.readFloat());
        }
    }

    @AllArgsConstructor
    public static class Component {
        final public String item;
        final public float yield;
        final public Int2ShortOpenHashMap weights;
        final public Int2FloatOpenHashMap multipliers;

        public static Int2FloatOpenHashMap parseFloatMap(List<Map<Object, Object>> floatEntries) {
            return floatEntries == null ? new Int2FloatOpenHashMap() : new Int2FloatOpenHashMap(
                    floatEntries.stream()
                            .map(dimEntry -> dimEntry.entrySet().iterator().next())
                            .collect(Collectors.toMap(
                                    firstEntry -> ((Number) firstEntry.getKey()).intValue(),
                                    firstEntry -> ((Number) firstEntry.getValue()).floatValue())));
        }

        public static Int2ShortOpenHashMap parseMapF2S(List<Map<Object, Object>> floatEntries) {
            return floatEntries == null ? new Int2ShortOpenHashMap() : new Int2ShortOpenHashMap(
                            floatEntries.stream()
                            .map(dimEntry -> dimEntry.entrySet().iterator().next())
                            .collect(Collectors.toMap(
                                    firstEntry -> ((Number) firstEntry.getKey()).intValue(),
                                    firstEntry -> VeinUtils.percentToShort(((Number) firstEntry.getValue()).floatValue()))));
        }

        public ByteBuf serialize() {
            int numBytes = (2 + item.length()) + 4 + (weights.size() * 6) + (multipliers.size() * 8);  // map each member to its size in bytes
            ByteBuf serialData = Unpooled.directBuffer(numBytes);
            PacketBase.writeUTF(serialData, item);
            serialData.writeFloat(yield);

            serialData.writeInt(weights.size());
            for (var entry : weights.int2ShortEntrySet()) {
                serialData.writeInt(entry.getIntKey());
                serialData.writeShort(entry.getShortValue());
            }

            // write the size and then the data for the multipliers
            serialData.writeInt(multipliers.size());
            for (var entry : multipliers.int2FloatEntrySet()) {
                serialData.writeInt(entry.getIntKey());
                serialData.writeFloat(entry.getFloatValue());
            }

            return serialData;
        }

        public static Component deserialize(ByteBuf buf) {
            String item = PacketBase.readUTF(buf);
            float yield = buf.readFloat();

            // read weights which are stored first
            int numWeights = buf.readInt();
            Int2ShortOpenHashMap weights = new Int2ShortOpenHashMap(numWeights);
            for (int i = 0; i < numWeights; ++i) {
                weights.put(buf.readInt(), buf.readShort());
            }

            // read multipliers which are stored next
            int numMults = buf.readInt();
            Int2FloatOpenHashMap multipliers = new Int2FloatOpenHashMap(numMults);
            for (int i = 0; i < numMults; ++i) {
                multipliers.put(buf.readInt(), buf.readFloat());
            }

            return new Component(item, yield, weights, multipliers);
        }
    }

}
