package com.flansmod.warforge.api.vein.init;

import org.apache.commons.lang3.tuple.Pair;
import com.flansmod.warforge.api.vein.Quality;
import com.flansmod.warforge.api.vein.Vein;
import com.flansmod.warforge.api.vein.VeinKey;
import com.flansmod.warforge.server.ItemMatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ShortAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortRBTreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.flansmod.warforge.client.ClientProxy.VEIN_ENTRIES;
import static com.flansmod.warforge.common.WarForgeMod.LOGGER;
import static com.flansmod.warforge.common.WarForgeMod.VEIN_HANDLER;

public class VeinUtils {
    public static final short WEIGHT_FRACTION_TENS_POW = 10000;  // should stay 10,000 so that it fits within a short
    public final static short NULL_VEIN_ID = (short) 0x00_00_FF_FF;  // # of distinct qualities limited to 7

    public Short2ObjectOpenHashMap<Vein> ID_TO_VEINS = new Short2ObjectOpenHashMap<>();

    // we pack the chunk indices as x(higher bits) z(lower bits) and vein data as iteration id (higher bits) vein id (lower bits)
    // for the mega chunk occurrence data, the default return value set's first entry is the current weight
    private final Object2ObjectOpenHashMap<ResourceKey<Level>, Object2ShortAVLTreeMap<VeinKey>> DIM_VEIN_WEIGHT_MAP;  // dim -> [key->id map, .obj -> veinkey -> id
    private final Object2ObjectOpenHashMap<ResourceKey<Level>, Long2ObjectOpenHashMap<Pair<Short2ShortOpenHashMap, Short2ShortRBTreeMap>>> MEGA_CHUNK_OCCURRENCE_DATA;  // dim -> megchunk -> (id -> occurrences and short [byte-X, byte-Z] offsets -> id)

    public final short iterationId;
    public final short megachunkLength;
    public final short megachunkArea;

    public final double gradientCenterX;
    public final double gradientCenterZ;
    public final double gradientRadius;
    public final double gradientFrequency;
    public final boolean gradientEnabled;

    public boolean hasFinishedInit;

    protected VeinUtils(short iterationId, short megachunkLength) {
        this(iterationId, megachunkLength, 0.0, 0.0, 0.0, 1.0);
    }

    protected VeinUtils(short iterationId, short megachunkLength, double gradientCenterX, double gradientCenterZ, double gradientRadius, double gradientFrequency) {
        this.iterationId = iterationId;
        this.megachunkLength = megachunkLength;
        this.megachunkArea = (short) (megachunkLength * megachunkLength);
        ID_TO_VEINS.defaultReturnValue(null);

        this.gradientCenterX = gradientCenterX;
        this.gradientCenterZ = gradientCenterZ;
        this.gradientRadius = gradientRadius;
        this.gradientFrequency = gradientFrequency;
        this.gradientEnabled = gradientRadius > 0.0;

        DIM_VEIN_WEIGHT_MAP = new Object2ObjectOpenHashMap<>();
        MEGA_CHUNK_OCCURRENCE_DATA = new Object2ObjectOpenHashMap<>();
        hasFinishedInit = false;
    }

    public static short percentToShort(float percent) {
        percent *= VeinUtils.WEIGHT_FRACTION_TENS_POW;
        return (short) Math.round(percent);
    }

    // returns a formatted percent string, dropping the decimal part if it is 0; assumes 4 sig figs
    public static String shortToPercentStr(short percent) {
        int whole = percent / 100;
        int decimal = percent - whole * 100;
        String decimalStr = decimal > 0 ? "." + decimal + "%" : "%";
        return whole + decimalStr;
    }

    // return each identical item component's weight, guaranteed yield and percent yield amount, respectively
    // assumes comp and dim are both present and does not consider component weight
    public static ArrayList<short[]> getYieldInfo(ItemMatcher comp, Pair<Vein, Quality> veinInfo, ResourceKey<Level> dim) {
        Vein vein = veinInfo.getLeft();
        Quality qual = veinInfo.getRight();
        ArrayList<Object2FloatOpenHashMap<ResourceKey<Level>>> yields = vein.compYields.get(comp);  // comp -> LIST OF dim : yield
        ArrayList<Object2ShortOpenHashMap<ResourceKey<Level>>> weights = vein.compWeights.get(comp);
        assert yields.size() == weights.size();  // sanity check
        ArrayList<short[]> yieldInfos = new ArrayList<>(yields.size());  // result

        // for each sub-component (same item, different stats), store its weight, guaranteed yield, and percent yield
        for (int subCompIndex = 0; subCompIndex < yields.size(); ++subCompIndex) {
            float yield = yields.get(subCompIndex).getFloat(dim);

            // scale the guaranteed yield based on quality
            yield *= qual.getLocalMultiplier(vein);

            short[] result = new short[]{
                    weights.get(subCompIndex).getShort(dim),
                    (short) yield,
                    0
            };

            result[2] = percentToShort(yield - result[1]);

            yieldInfos.add(result);
        }

        return yieldInfos;
    }

    public boolean dimHasVeins(ResourceKey<Level> dim) {
        return DIM_VEIN_WEIGHT_MAP.containsKey(dim);
    }

    // gets weight without checking for existence of key
    public short getVeinId(ResourceKey<Level> dim, VeinKey key) {
        return DIM_VEIN_WEIGHT_MAP.get(dim).getShort(key);
    }

    // gets vein id and ensures if no veins exists in dimension that no error occurs
    public short getVeinIdSafe(ResourceKey<Level> dim, VeinKey key) {
        if (!dimHasVeins(dim)) { return NULL_VEIN_ID; }
        return getVeinId(dim, key);
    }

    // gets the vein occurrences without performing any checks
    public short getVeinOccurrences(ResourceKey<Level> dim, long megachunkKey, short veinId) {
        return MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey).getLeft().get(veinId);
    }

    // gets the vein occurrences and performs any necessary setup
    public short getVeinOccurrencesSafe(ResourceKey<Level> dim, long megachunkKey, short veinId) {
        ensureMegachunkPopulated(dim, megachunkKey);
        return getVeinOccurrences(dim, megachunkKey, veinId);
    }

    // because this is only based on coordinate and seed, we don't need to store data we get from this which doesn't change
    public int[] generateChunkHash(int chunk_x, int chunk_z, long seed) {
        // "hash" to determine the vein for this chunk
        int hash = (int) (seed * 2654435761L);
        hash = (int) ((hash + chunk_x) * 2654435761L);
        hash = (int) ((hash + chunk_z) * 2654435761L);
        hash = (hash << 1) >>> 1;  // ensure non-negative

        int quality = hash % Quality.values().length;  // quality factor possibilities of poor, fair, rich
        //hash %= 10000;  we hash it later, for now we just want the raw value

        return new int[]{hash, quality};
    }

    public double gradientDensity(int chunkX, int chunkZ) {
        if (!gradientEnabled) { return 1.0; }
        double blockX = chunkX * 16.0 + 8.0;
        double blockZ = chunkZ * 16.0 + 8.0;
        double dist = Math.max(Math.abs(blockX - gradientCenterX), Math.abs(blockZ - gradientCenterZ));
        double t = dist / gradientRadius;
        if (t >= 1.0) { return 0.0; }
        return 1.0 - Math.pow(t, gradientFrequency);
    }

    public int gradientGateHash(int chunkX, int chunkZ, long seed) {
        int hash = (int) ((seed ^ 0x9E3779B97F4A7C15L) * 0x27D4EB2F165667C5L);
        hash = (int) ((hash + chunkX) * 0x85EBCA6B);
        hash = (int) ((hash + chunkZ) * 0xC2B2AE35);
        hash ^= (hash >>> 15);
        return (hash << 1) >>> 1;
    }

    public boolean gradientGatesOut(int chunkX, int chunkZ, long seed) {
        double density = gradientDensity(chunkX, chunkZ);
        if (density >= 1.0) { return false; }
        if (density <= 0.0) { return true; }
        double roll = gradientGateHash(chunkX, chunkZ, seed) / 2147483648.0;
        return roll >= density;
    }

    public long produceMegachunkKey(int chunkX, int chunkZ) {
        // we shift by one less than the megachunkLength to ensure that SE is 0-0, NE is 0-[-1], SW is -1-0, etc
        long megachunkKey = (chunkX - megachunkLength + 1) / megachunkLength;
        megachunkKey = megachunkKey << 32;  // shift X into higher bits
        megachunkKey += (chunkZ - megachunkLength + 1) / megachunkLength;
        return megachunkKey;
    }

    // we store the chunk offset as offsetx-offsetz, with each being the absolute
    // offset from the smallest magnitude chunk coordinates within a given megachunk
    // because we limit the megachunk length to 180, we still need 8 bits to store offset information
    public short produceChunkOffset(int chunkX, int chunkZ) {
        // for negative indices, their actual chunk remainder starts at -1, which should be 0
        if (chunkX < 0) { ++chunkX; }
        if (chunkZ < 0) { ++chunkZ; }
        short offset = (short) (Math.abs(chunkX) % megachunkLength << 8);
        offset += (short) (Math.abs(chunkZ) % megachunkLength);
        return offset;
    }

    // returns the chunkX and chunkZ coordinates in an int array
    public int[] recoverChunkCoords(short offset, short megachunkLength, long megachunkKey) {
        int megachunkX = (int) (megachunkKey >>> 32);
        int megachunkZ = (int) megachunkKey;

        int chunkX = offset >>> 8;
        int chunkZ = offset & 255;

        // -1 megachunk coords start at the origin, not having traveled a megachunk length
        // individual chunk coords in the negative direction will have an offset one lower than their coord
        if (megachunkX < 0) {
            ++megachunkX;
            --chunkX;
        }
        if (megachunkZ < 0) {
            ++megachunkZ;
            --chunkZ;
        }

        chunkX += megachunkX * megachunkLength;
        chunkZ += megachunkZ * megachunkLength;

        return new int[]{chunkX, chunkZ};
    }

    public Vein getVein(short veinId) {
        return ID_TO_VEINS.get(veinId);
    }

    // returns null to indicate null vein
    public Pair<Vein, Quality> getVein(ResourceKey<Level> dim, int chunkX, int chunkZ, long seed) {
        long megachunkKey = produceMegachunkKey(chunkX, chunkZ);
        short offset = produceChunkOffset(chunkX, chunkZ);

        if (!dimHasVeins(dim)) { return null; }  // we don't need to store any data for empty dimensions

        // check if the world has our data
        Pair<Vein, Quality> veinInfo = pullVein(dim, megachunkKey, offset);
        if (veinInfo != null) { return veinInfo.getLeft() == null ? null : veinInfo; }

        // generate the vein info and extract the veinId and quality
        veinInfo = generateVeinInfo(dim, megachunkKey, chunkX, chunkZ, seed);
        short veinId = veinInfo == null ? NULL_VEIN_ID : veinInfo.getLeft().getId();
        int quality = veinInfo == null ? 7 : veinInfo.getRight().ordinal();

        // add the vein info to storage and then return it
        addVeinInfo(dim, megachunkKey, offset, veinId, quality);
        return veinInfo;
    }

    public Pair<Vein, Quality> peekStoredVein(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return pullVein(dim, produceMegachunkKey(chunkX, chunkZ), produceChunkOffset(chunkX, chunkZ));
    }

    public Vein findVein(String identifier) {
        if (identifier == null) { return null; }
        String trimmed = identifier.trim();
        try {
            Vein byId = ID_TO_VEINS.get(Short.parseShort(trimmed));
            if (byId != null) { return byId; }
        } catch (NumberFormatException ignored) { }
        for (Vein vein : ID_TO_VEINS.values()) {
            if (vein.translationKey.equalsIgnoreCase(trimmed)) { return vein; }
        }
        return null;
    }

    private void ensureMegachunkStorage(ResourceKey<Level> dim, long megachunkKey) {
        if (isMegachunkPopulated(dim, megachunkKey)) { return; }
        if (DIM_VEIN_WEIGHT_MAP.containsKey(dim)) {
            populateMegachunkInfo(dim, megachunkKey);
            return;
        }
        var dimMap = MEGA_CHUNK_OCCURRENCE_DATA.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>());
        var pair = Pair.of(new Short2ShortOpenHashMap(), new Short2ShortRBTreeMap());
        pair.getLeft().defaultReturnValue(WEIGHT_FRACTION_TENS_POW);
        pair.getRight().defaultReturnValue(NULL_VEIN_ID);
        dimMap.put(megachunkKey, pair);
    }

    public void setVeinOverride(ResourceKey<Level> dim, int chunkX, int chunkZ, short veinId, int quality) {
        long megachunkKey = produceMegachunkKey(chunkX, chunkZ);
        short offset = produceChunkOffset(chunkX, chunkZ);
        ensureMegachunkStorage(dim, megachunkKey);

        var currMegachunk = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);
        var occurrences = currMegachunk.getLeft();
        var offsets = currMegachunk.getRight();

        if (offsets.containsKey(offset)) {
            short oldId = splitVeinInfo(offsets.get(offset))[0];
            if (oldId != NULL_VEIN_ID && occurrences.containsKey(oldId)) {
                occurrences.put(oldId, (short) Math.max(0, occurrences.get(oldId) - 1));
            }
        }

        short info = (veinId == NULL_VEIN_ID) ? NULL_VEIN_ID : compressVeinInfo(veinId, quality);
        offsets.put(offset, info);
        if (veinId != NULL_VEIN_ID && occurrences.containsKey(veinId)) {
            occurrences.put(veinId, (short) (occurrences.get(veinId) + 1));
        }

        recomputeRemainingWeight(dim, megachunkKey);
    }

    public void clearVeinAt(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        setVeinOverride(dim, chunkX, chunkZ, NULL_VEIN_ID, 7);
    }

    public Pair<Vein, Quality> rerollVeinAt(ResourceKey<Level> dim, int chunkX, int chunkZ, long seed) {
        long megachunkKey = produceMegachunkKey(chunkX, chunkZ);
        short offset = produceChunkOffset(chunkX, chunkZ);
        if (isMegachunkPopulated(dim, megachunkKey)) {
            var currMegachunk = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);
            if (currMegachunk.getRight().containsKey(offset)) {
                short oldId = splitVeinInfo(currMegachunk.getRight().get(offset))[0];
                currMegachunk.getRight().remove(offset);
                if (oldId != NULL_VEIN_ID && currMegachunk.getLeft().containsKey(oldId)) {
                    currMegachunk.getLeft().put(oldId, (short) Math.max(0, currMegachunk.getLeft().get(oldId) - 1));
                }
                recomputeRemainingWeight(dim, megachunkKey);
            }
        }
        return getVein(dim, chunkX, chunkZ, seed);
    }

    private void recomputeRemainingWeight(ResourceKey<Level> dim, long megachunkKey) {
        if (!isMegachunkPopulated(dim, megachunkKey)) { return; }
        var occurrences = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey).getLeft();
        int remaining = WEIGHT_FRACTION_TENS_POW;
        for (var entry : occurrences.short2ShortEntrySet()) {
            Vein vein = ID_TO_VEINS.get(entry.getShortKey());
            if (vein == null) { continue; }
            if (entry.getShortValue() >= vein.getDimExpectedCount(dim)) {
                remaining -= vein.getDimWeight(dim);
            }
        }
        occurrences.defaultReturnValue((short) Math.max(0, remaining));
    }

    public boolean isMegachunkPopulated(ResourceKey<Level> dim, long megachunkKey) {
        return MEGA_CHUNK_OCCURRENCE_DATA.containsKey(dim) && MEGA_CHUNK_OCCURRENCE_DATA.get(dim).containsKey(megachunkKey);
    }

    public void ensureMegachunkPopulated(ResourceKey<Level> dim, long megachunkKey) {
        if (!isMegachunkPopulated(dim, megachunkKey) && DIM_VEIN_WEIGHT_MAP.containsKey(dim)) {
            populateMegachunkInfo(dim, megachunkKey);
        }
    }

    // should only be called for dimensions which actually expect to see veins within them
    public void populateMegachunkInfo(ResourceKey<Level> dim, long megachunkKey) {
        // if the dimension has never been initialized, but should have weights, then initialize it
        var currDimMegachunks = MEGA_CHUNK_OCCURRENCE_DATA.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>());
        currDimMegachunks.put(megachunkKey, Pair.of(
            new Short2ShortOpenHashMap(),
            new Short2ShortRBTreeMap()
        ));

        // get a reference to the current megachunk and intialize it
        var currMegachunk = currDimMegachunks.get(megachunkKey);
        for (var entry : DIM_VEIN_WEIGHT_MAP.get(dim).object2ShortEntrySet()) {
            if (entry.getKey() == VeinKey.NULL_KEY) { continue; }  // ignore the null key
            currMegachunk.getLeft().put(entry.getShortValue(), (short) 0);
        }

        // first map stores id -> occurrences map, second stores offset -> id map
        currMegachunk.getLeft().defaultReturnValue(WEIGHT_FRACTION_TENS_POW);  // first has default rv of weight left
        currMegachunk.getRight().defaultReturnValue(NULL_VEIN_ID);  // second just indicates no such coordinate is present
    }

    // decompresses the vein info, with separate handling for server and client, returning null for the null vein and
    // a pair of null values for an unrecognized vein on the client
    public static Pair<Vein, Quality> decompressVeinInfo(short veinInfo) {
        if (veinInfo == NULL_VEIN_ID) { return null; }

        short[] decompVeinInfo = splitVeinInfo(veinInfo);
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return Pair.of(VEIN_HANDLER.ID_TO_VEINS.get(decompVeinInfo[0]), Quality.getQuality(decompVeinInfo[1]));
        } else {
            Vein targetVein = VEIN_ENTRIES.get(decompVeinInfo[0]);
            if (targetVein == null) { return Pair.of(null, null); }  // use this to indicate we don't know the vein
            return Pair.of(targetVein, Quality.getQuality(decompVeinInfo[1]));
        }
    }

    public static short[] splitVeinInfo(short veinInfo) {
        if (veinInfo == NULL_VEIN_ID) { return new short[]{NULL_VEIN_ID, (short) 7}; }  // null vein id is also its info
        return new short[]{(short) (veinInfo & 0x00_00_1F_FF), (short) ((veinInfo & 0x00_00_E0_00) >> 13)};
    }

    public short compressVeinInfo(int veinId, int qualityIndex) {
        return (short) (veinId | (qualityIndex << 13));
    }

    public short compressVeinInfo(Pair<Vein, Quality> veinInfo) {
        if (veinInfo == null) { return NULL_VEIN_ID; }
        return compressVeinInfo(veinInfo.getLeft().getId(), veinInfo.getRight().ordinal());
    }

    // does not assume megachunk is populated
    public Pair<Vein, Quality> pullVein(ResourceKey<Level> dim, long megachunkKey, short offset) {
        // check if the vein exists
        if (!isMegachunkPopulated(dim, megachunkKey)) { return null; }

        var offsetIds = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey).getRight();
        if (!offsetIds.containsKey(offset)) { return null; }  // does logarithmic search; allows drv of null vein

        // the null vein does not correspond to any vein or quality, but does actually exist
        short chunkData = offsetIds.get(offset);
        if (chunkData == NULL_VEIN_ID) { return Pair.of(null, null); }

        // format the data correctly and return the result
        short[] unpackedData = splitVeinInfo(chunkData);
        return Pair.of(ID_TO_VEINS.get(unpackedData[0]), Quality.getQuality(unpackedData[1]));
    }

    // returns the vein info for the current chunk, with null being returned for "no vein"
    public Pair<Vein, Quality> generateVeinInfo(ResourceKey<Level> dim, long megachunkKey, int chunkX, int chunkZ, long seed) {
        if (!DIM_VEIN_WEIGHT_MAP.containsKey(dim)) { return null; }  // if we cannot generate any chunks for this dim, return null
        ensureMegachunkPopulated(dim, megachunkKey);

        if (gradientEnabled && gradientGatesOut(chunkX, chunkZ, seed)) { return null; }

        int[] chunkHash = generateChunkHash(chunkX, chunkZ, seed);
        Object2ShortAVLTreeMap<VeinKey> currDimWeights = DIM_VEIN_WEIGHT_MAP.get(dim);
        var currMegachunk = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);

        // we limit the hash result based on the remaining weight in the megachunk, then skip over veins which exceed
        // their expected value [floor(megachunkLength^2 * weight / 1000)]
        short weightRemaining = currMegachunk.getLeft().defaultReturnValue();
        boolean doRandomRoll = weightRemaining == 0;

        // get the vein id from the hash by converting it into a vein key
        short trimmedHash = (short) (chunkHash[0] % weightRemaining);
        VeinKey currVeinKey = new VeinKey(trimmedHash);
        short currID = currDimWeights.getShort(currVeinKey);

        // return null for the null vein, or proceed
        Vein currVein = ID_TO_VEINS.get(currID);
        short dimExpCount = getDimExpCount(currVein, dim);

        // if some vein has occurred too many times, we may need to skip over it if we select it
        if (!doRandomRoll && weightRemaining < WEIGHT_FRACTION_TENS_POW) {
            int skipGuard = ID_TO_VEINS.size() + 1;
            while (currMegachunk.getLeft().get(currID) >= dimExpCount) {
                short step = getDimWeight(currVein, dim);
                if (step <= 0 || --skipGuard < 0) { break; }
                trimmedHash += step;
                currVeinKey.rebaseKey(trimmedHash);
                currID = currDimWeights.getShort(currVeinKey);
                currVein = ID_TO_VEINS.get(currID);
            }
        }

        if (currVein == null) { return null; }  // dont return a pair with null info
        return Pair.of(currVein, Quality.getQuality(chunkHash[1]));
    }

    public short getDimWeight(Vein vein, ResourceKey<Level> dim) {
        return vein == null ? DIM_VEIN_WEIGHT_MAP.get(dim).defaultReturnValue() : vein.getDimExpectedCount(dim);
    }

    public short getDimExpCount(Vein vein, ResourceKey<Level> dim) {
        return vein == null ? DIM_VEIN_WEIGHT_MAP.get(dim).getShort(VeinKey.NULL_KEY) : vein.getDimExpectedCount(dim);
    }

    public void addVeinInfo(ResourceKey<Level> dim, int chunkX, int chunkZ, short veinId, int quality) {
        long megachunkKey = produceMegachunkKey(chunkX, chunkZ);
        short offset = produceChunkOffset(chunkX, chunkZ);

        // don't add vein info that we already have
        addVeinInfo(dim, megachunkKey, offset, veinId, quality);
    }

    // does check to ensure the targeted megachunk is populated; will accept a veinId of NULL VEIN ID
    public void addVeinInfo(ResourceKey<Level> dim, long megachunkKey, short offset, short veinId, int quality) {
        // check the megachunk is present and collect it
        ensureMegachunkPopulated(dim, megachunkKey);
        var currMegachunk = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);

        // we need to update the number of occurrences and adjust the remaining weight accordingly
        int currOccurrences = currMegachunk.getLeft().get(veinId);
        currMegachunk.getLeft().put(veinId, (short) (currOccurrences + 1));
        Vein currVein = ID_TO_VEINS.get(veinId);  // will be null for the null vein

        // we now need to place the offset position info
        short veinData;
        if (veinId == NULL_VEIN_ID) { veinData = NULL_VEIN_ID; }
        else { veinData = compressVeinInfo(veinId, quality); }
        currMegachunk.getRight().put(offset, veinData);

        // if all veins exceeded expected don't do any check
        if (currMegachunk.getLeft().defaultReturnValue() == 0) { return; }

        short expectedCount = getDimExpCount(currVein, dim);

        // check if we need to update the weight - when remaining weight is 0 we are just picking randomly again
        if (currMegachunk.getLeft().defaultReturnValue() > 0 && currOccurrences + 1 >= expectedCount) {
            int dimWeight = getDimWeight(currVein, dim);
            currMegachunk.getLeft().defaultReturnValue((short) (currMegachunk.getLeft().defaultReturnValue() - dimWeight));
        }
    }

    private void populateDimVeinMap(ResourceKey<Level> dim) {
        // for later sorting
        ArrayList<Vein> smallestWeights = new ArrayList<>();
        ArrayList<Vein> mediumWeights = new ArrayList<>();
        ArrayList<Vein> largerWeights = new ArrayList<>();
        ArrayList<Vein> largestWeights = new ArrayList<>();

        // for ease of use
        ArrayList<ArrayList<Vein>> sortedVeins = new ArrayList<>();
        sortedVeins.add(smallestWeights);
        sortedVeins.add(mediumWeights);
        sortedVeins.add(largerWeights);
        sortedVeins.add(largestWeights);
        short remainingWeight = WEIGHT_FRACTION_TENS_POW;

        var dimWeightMap = DIM_VEIN_WEIGHT_MAP.get(dim);

        // attempt to categorize and store all veins
        for (Vein currVein : ID_TO_VEINS.values()) {
            short currDimWeight = currVein.getDimWeight(dim);
            if (currDimWeight == 0) { continue; }  // if the vein has no weight in this dim, ignore it
            // check if we have exceeded the weight provided
            if (remainingWeight < currDimWeight) {
                LOGGER.atError().log("The maximum weight (10000) has been exceeded at vein " + currVein.translationKey
                        + " in dim " + dim.location() + "; ignoring current vein.");
                continue;
            } else {
                remainingWeight -= currDimWeight;
            }

            // sort into rough categories based on likelihood
            if (currDimWeight <= 1250) { smallestWeights.add(currVein); }
            else if (currDimWeight <= 2500) { mediumWeights.add(currVein); }
            else if (currDimWeight <= 5000) { largerWeights.add(currVein); }
            else { largestWeights.add(currVein); }
        }

        final short nullWeight = remainingWeight;

        // we need to add a key corresponding to the null vein weight left
        if (nullWeight > 0) {
            // sort into rough categories based on likelihood
            if (nullWeight <= 1250) { smallestWeights.add(null); }
            else if (nullWeight <= 2500) { mediumWeights.add(null); }
            else if (nullWeight <= 5000) { largerWeights.add(null); }
            else { largestWeights.add(null); }
        }

        // sort based on weight in ascending order
        Comparator<Vein> weight_sorter = (vein1, vein2) -> {
            short weight1 = vein1 == null ? nullWeight : vein1.getDimWeight(dim);
            short weight2 = vein2 == null ? nullWeight : vein2.getDimWeight(dim);

            return Short.compare(weight1, weight2);
        };

        smallestWeights.sort(weight_sorter);
        mediumWeights.sort(weight_sorter);
        largerWeights.sort(weight_sorter);
        largestWeights.sort(weight_sorter);

        // we will assign key bounds such that the least likely veins end up with the most extreme bounds
        // while the most likely veins have the intermediate bounds
        // this should hopefully bias the most likely veins towards the top of the tree
        for (int i = 0; i < sortedVeins.size(); ++i) {
            ArrayList<Vein> currVeinCategory = sortedVeins.get(i);
            int halfPoint = (currVeinCategory.size() + 1) >> 1;
            for (int j = 0; j < halfPoint; ++j) {
                Vein currVein = currVeinCategory.get(j);
                short currWeight = nullWeight;
                short currID = NULL_VEIN_ID;
                if (currVein != null) {
                    currWeight = currVein.getDimWeight(dim);
                    currID = currVein.getId();
                }

                dimWeightMap.put(new VeinKey(currWeight, false), currID);
            }
        }

        // insert from largest to smallest
        for (int i = sortedVeins.size() - 1; i >= 0; --i) {
            ArrayList<Vein> currVeinCategory = sortedVeins.get(i);
            int halfPoint = (currVeinCategory.size() + 1) >> 1;
            for (int j = halfPoint; j < currVeinCategory.size(); ++j) {
                // get the vein and its details; we may insert a null vein so we need to check
                Vein currVein = currVeinCategory.get(j);
                short currWeight = nullWeight;
                short currID = NULL_VEIN_ID;
                if (currVein != null) {
                    currWeight = currVein.getDimWeight(dim);
                    currID = currVein.getId();
                }

                dimWeightMap.put(new VeinKey(currWeight, false), currID);
            }
        }

        // we need to store data about the null vein
        // default return value is the null vein weight, while the null key returns the expected null vein occurrences
        dimWeightMap.defaultReturnValue(nullWeight);
        dimWeightMap.put(VeinKey.NULL_KEY, (short) (VEIN_HANDLER.megachunkArea * nullWeight / VeinUtils.WEIGHT_FRACTION_TENS_POW));
    }

    protected void populateVeinMap(@Nullable List<VeinConfigHandler.VeinEntry> veinEntries) {
        if (veinEntries == null || veinEntries.size() == 0) { return; }  // if we are passed null then we did not read any entries

        for (VeinConfigHandler.VeinEntry entry : veinEntries) {
            Vein currVein = new Vein(entry);  // store all veins provided for later use
            ID_TO_VEINS.put(currVein.getId(), currVein);

            // determine the dims this vein is present in
            for (ResourceKey<Level> dim : currVein.getValidDims()) {
                DIM_VEIN_WEIGHT_MAP.put(dim, new Object2ShortAVLTreeMap<>());
            }
        }

        for (ResourceKey<Level> dim : DIM_VEIN_WEIGHT_MAP.keySet()) {
            populateDimVeinMap(dim);
        }

        hasFinishedInit = true;
    }

    private String getVeinInfoID(short veinId) {
        return "vinfo_" + veinId;
    }
    private String getDimInfoID(ResourceKey<Level> dim) { return "dinfo_" + dim.location().toString(); }

    public void readFromNBT(CompoundTag tags) {
        if (!tags.contains("vein_dims")) { return; }
        // megachunk length controls how chunk offsets are packed; if it changed we cannot reuse the stored data
        if (tags.contains("vein_megachunk_length") && tags.getShort("vein_megachunk_length") != megachunkLength) {
            LOGGER.info("Vein megachunk_length changed; discarding stored vein data so chunk offsets can repack.");
            return;
        }

        CompoundTag dims = tags.getCompound("vein_dims");
        for (String dimKey : dims.getAllKeys()) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(dimKey.substring("dinfo_".length()));
            if (dimLoc == null) { continue; }
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);
            if (!DIM_VEIN_WEIGHT_MAP.containsKey(dim)) { continue; }  // no veins configured for this dim now; drop its data

            CompoundTag currDim = dims.getCompound(dimKey);
            ListTag megachunks = currDim.getList("megachunks", Tag.TAG_COMPOUND);  // compound tags for tag lists
            for (int megachunkTagIndex = 0; megachunkTagIndex < megachunks.size(); ++megachunkTagIndex) {
                CompoundTag currMegachunkNBT = megachunks.getCompound(megachunkTagIndex);
                long megachunkKey = currMegachunkNBT.getLong("key");
                ensureMegachunkPopulated(dim, megachunkKey);
                var currMegachunk = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);

                // iterate over every vein tag actually stored so removed veins can be reconciled to the null vein
                CompoundTag veins = currMegachunkNBT.getCompound("veins");
                for (String veinTag : veins.getAllKeys()) {
                    short storedId;
                    try { storedId = Short.parseShort(veinTag.substring("vinfo_".length())); }
                    catch (NumberFormatException e) { continue; }

                    // a vein removed from the config is wiped: its chunks become the permanent null vein
                    boolean veinStillExists = storedId == NULL_VEIN_ID || ID_TO_VEINS.containsKey(storedId);
                    short effectiveId = veinStillExists ? storedId : NULL_VEIN_ID;

                    ListTag veinData = veins.getList(veinTag, Tag.TAG_BYTE_ARRAY);
                    for (int veinOccurrences = 0; veinOccurrences < veinData.size(); ++veinOccurrences) {
                        byte[] rawVeinData = ((ByteArrayTag) veinData.get(veinOccurrences)).getAsByteArray();
                        if (rawVeinData.length < 2) { continue; }
                        short offset = (short) (((int) rawVeinData[0]) << 8);
                        offset += rawVeinData[1];

                        short veinInfo;
                        if (effectiveId == NULL_VEIN_ID) { veinInfo = NULL_VEIN_ID; }
                        else { veinInfo = compressVeinInfo(effectiveId, rawVeinData.length > 2 ? rawVeinData[2] : 0); }

                        currMegachunk.getRight().put(offset, veinInfo);
                        if (effectiveId != NULL_VEIN_ID && currMegachunk.getLeft().containsKey(effectiveId)) {
                            currMegachunk.getLeft().put(effectiveId, (short) (currMegachunk.getLeft().get(effectiveId) + 1));
                        }
                    }
                }

                recomputeRemainingWeight(dim, megachunkKey);
            }
        }
    }

    public void WriteToNBT(CompoundTag tags) {
        tags.putShort("vein_it_id", VEIN_HANDLER.iterationId);
        tags.putShort("vein_megachunk_length", VEIN_HANDLER.megachunkLength);
        CompoundTag dims = new CompoundTag();

        // iterate over each dimension with occurrence data
        var writtenDims = MEGA_CHUNK_OCCURRENCE_DATA.keySet();
        for (ResourceKey<Level> dim : writtenDims) {
            CompoundTag currDim = new CompoundTag();

            ListTag megachunks = new ListTag();
            currDim.put("megachunks", megachunks);

            // get every chunk offset and corresponding vein qual+id short to store them (encodes occurrence data by # offsets)
            for (long megachunkKey : MEGA_CHUNK_OCCURRENCE_DATA.get(dim).keySet()) {
                var currMegachunkData = MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey);
                CompoundTag currMegachunkNBT = new CompoundTag();
                currMegachunkNBT.putLong("key", megachunkKey);

                CompoundTag veins = new CompoundTag();
                currMegachunkNBT.put("veins", veins);

                // for each occurrence of a vein, store it under the vein id
                for (var entry : MEGA_CHUNK_OCCURRENCE_DATA.get(dim).get(megachunkKey).getRight().short2ShortEntrySet()) {
                    // get id and qual information and make sure the tag for that vein exists
                    short[] idQual = splitVeinInfo(entry.getShortValue());
                    String veinTagID = getVeinInfoID(idQual[0]);
                    if (!veins.contains(veinTagID)) {
                        veins.put(veinTagID, new ListTag());
                    }

                    // for every occurrence, tie the offset and quality to the vein
                    byte offsetX = (byte) (entry.getShortKey() >>> 8);
                    byte offsetZ = (byte) (entry.getShortKey());
                    byte qual = (byte) (idQual[1]);

                    byte[] bytesToStore;
                    if (idQual[0] == NULL_VEIN_ID) { bytesToStore = new byte[]{offsetX, offsetZ}; }
                    else { bytesToStore = new byte[]{offsetX, offsetZ, qual}; }

                    // store the data
                    ListTag veinOffsetQualBEBytes = veins.getList(veinTagID, Tag.TAG_BYTE_ARRAY);
                    veinOffsetQualBEBytes.add(new ByteArrayTag(bytesToStore));
                }

                megachunks.add(currMegachunkNBT);  // store the curr megachunk in the list of megachunks
            }

            dims.put(getDimInfoID(dim), currDim);  // store the current dim data in the compound tag of all vein dims
        }

        tags.put("vein_dims", dims);
    }
}
