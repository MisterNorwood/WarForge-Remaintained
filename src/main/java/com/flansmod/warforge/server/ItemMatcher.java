package com.flansmod.warforge.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;

/**
 * A value-equal reference to either a concrete {@link Item} or an item {@link TagKey}. Matching and
 * resolution use native {@link ItemStack#is} / {@link Ingredient}; 1.20.1 has no metadata-backed item
 * variants. Modelled as a sealed item/tag pair so it carries the resolved object directly (no
 * stringly-typed id) and, being records, is safe to use as a map/set key.
 */
public sealed interface ItemMatcher permits ItemMatcher.OfItem, ItemMatcher.OfTag {

    boolean matches(ItemStack stack);

    Ingredient toIngredient();

    /** Representative stack; tags resolve to their {@code index}-th member. EMPTY if unresolvable. */
    ItemStack toStack(int amount, int index);

    default ItemStack toStack(int amount) {
        return toStack(amount, 0);
    }

    default ItemStack toStack() {
        return toStack(1, 0);
    }

    boolean isTag();

    /** The registry/tag id string (for display + serialization). */
    String id();

    void write(FriendlyByteBuf buf);

    // --- factories ---

    static ItemMatcher of(Item item) {
        return new OfItem(item);
    }

    static ItemMatcher of(TagKey<Item> tag) {
        return new OfTag(tag);
    }

    /** Resolve an item id; null if the id is malformed or no such item is registered. */
    @Nullable
    static ItemMatcher ofItem(String itemId) {
        // 1.20.1 has no item metadata; drop any legacy "mod:item:meta" suffix
        int last = itemId.lastIndexOf(':');
        String stripped = (last != itemId.indexOf(':')) ? itemId.substring(0, last) : itemId;
        ResourceLocation rl = ResourceLocation.tryParse(stripped);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        return new OfItem(BuiltInRegistries.ITEM.get(rl));
    }

    /** Build a tag matcher; null if the id is not a valid resource location. */
    @Nullable
    static ItemMatcher ofTag(String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        return rl == null ? null : new OfTag(TagKey.create(Registries.ITEM, rl));
    }

    /** Auto-detect: an existing item tag becomes a tag matcher, otherwise an item matcher. */
    @Nullable
    static ItemMatcher parse(String resource) {
        ResourceLocation rl = ResourceLocation.tryParse(resource);
        if (rl != null) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, rl);
            if (BuiltInRegistries.ITEM.getTag(tag).isPresent()) {
                return new OfTag(tag);
            }
        }
        return ofItem(resource);
    }

    @Nullable
    static ItemMatcher read(FriendlyByteBuf buf) {
        boolean tag = buf.readBoolean();
        String id = buf.readUtf();
        return tag ? ofTag(id) : ofItem(id);
    }

    record OfItem(Item item) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.is(item);
        }

        @Override
        public Ingredient toIngredient() {
            return Ingredient.of(item);
        }

        @Override
        public ItemStack toStack(int amount, int index) {
            return new ItemStack(item, amount);
        }

        @Override
        public boolean isTag() {
            return false;
        }

        @Override
        public String id() {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(false);
            buf.writeUtf(id());
        }
    }

    record OfTag(TagKey<Item> tag) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.is(tag);
        }

        @Override
        public Ingredient toIngredient() {
            return Ingredient.of(tag);
        }

        @Override
        public ItemStack toStack(int amount, int index) {
            ItemStack[] items = Ingredient.of(tag).getItems();
            if (index < 0 || index >= items.length) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = items[index].copy();
            stack.setCount(amount);
            return stack;
        }

        @Override
        public boolean isTag() {
            return true;
        }

        @Override
        public String id() {
            return tag.location().toString();
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(true);
            buf.writeUtf(id());
        }
    }

    /** Player-inventory match result for a required count (display/consumption helper). */
    record MatchResult(ItemMatcher matcher, int has, int required) {
        public boolean isEnough() {
            return has > required;
        }
    }
}
