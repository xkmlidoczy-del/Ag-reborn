package cn.academy.port.city;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Read authored NBT templates once and expose independent columns for world generation. */
final class CityTemplateLibrary {
    private static final ConcurrentHashMap<String, Template> CACHE = new ConcurrentHashMap<>();

    private CityTemplateLibrary() {}

    static boolean entranceThreshold(int width, int depth, int x, int z) {
        int middle = (width - 1) / 2;
        return width <= 48 && depth <= 48 && z == 5 && x >= middle - 2 && x <= middle + 1;
    }

    static Column at(long seed, int x, int z) {
        return at(x, z, CityTemplateSites.at(seed, x, z));
    }
    static Column at(int x, int z, CityTemplateSites.Site site) {
        return at(x,z,site,false);
    }
    static Column at(int x,int z,CityTemplateSites.Site site,boolean interiors) {
        if (site == null) return null;
        Template template = CACHE.computeIfAbsent((interiors ? "v1/" : "")+site.id(), CityTemplateLibrary::load);
        int insetX = (site.reservedWidth() - template.width) / 2;
        int insetZ = (site.reservedDepth() - template.depth) / 2;
        return new Column(template, x - site.originX() - insetX, z - site.originZ() - insetZ);
    }

    record Column(Template template, int x, int z) {
        int top() { return template.height - 2; }
        /** Only the unoccupied strip north of a normal 48×48 building lot. */
        boolean northSetback() { return z < 0 && template.width <= 48 && template.depth <= 48; }

        BlockState block(int y) {
            if (y < -1) return null;
            BlockState value = template.get(x, y + 1, z);
            if (y == -1 && value == null) return CityBlocks.block("plaza_tile").defaultBlockState();
            // The original podium floor is a full block above the forecourt. A half-step
            // at its existing doorway allows ordinary walking without changing the NBT.
            if (y == 0 && value != null && entranceThreshold(template.width, template.depth, x, z)) {
                String slab = value.is(CityBlocks.block("brushed_steel")) ? "steel_slab" : "white_panel_slab";
                return CityBlocks.block(slab).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            }
            return value == null ? Blocks.AIR.defaultBlockState() : value;
        }
    }

    private static Template load(String id) {
        boolean revised=id.startsWith("v1/");if(revised)id=id.substring(3);
        String path = "data/academy/structure/city/" + (revised ? "interior_v1/" : id.equals("grand_complex") ? "" : "library/") + id + ".nbt";
        try (var stream = CityTemplateLibrary.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing city structure " + path);
            CompoundTag nbt = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            var size = nbt.getList("size", Tag.TAG_INT);
            int width = size.getInt(0), height = size.getInt(1), depth = size.getInt(2);
            var paletteTag = nbt.getList("palette", Tag.TAG_COMPOUND);
            if (paletteTag.size() > 255 || height > 1024 || width <= 0 || depth <= 0)
                throw new IllegalStateException("Unsupported city template bounds/palette: " + id);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < palette.length; i++)
                palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
            var blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
            int[] sizes = new int[width * depth];
            for (int i = 0; i < blocks.size(); i++) {
                var p = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
                int x = p.getInt(0), y = p.getInt(1), z = p.getInt(2);
                if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth)
                    throw new IllegalStateException("Block outside city template: " + id);
                sizes[z * width + x]++;
            }
            int[][] columns = new int[sizes.length][];
            for (int i = 0; i < sizes.length; i++) if (sizes[i] != 0) columns[i] = new int[sizes[i]];
            Arrays.fill(sizes, 0);
            for (int i = 0; i < blocks.size(); i++) {
                var row = blocks.getCompound(i); var p = row.getList("pos", Tag.TAG_INT);
                int column = p.getInt(2) * width + p.getInt(0), state = row.getInt("state");
                if (state < 0 || state >= palette.length || row.contains("nbt"))
                    throw new IllegalStateException("Invalid or block-entity city template entry: " + id);
                columns[column][sizes[column]++] = (p.getInt(1) << 8) | state;
            }
            for (var column : columns) if (column != null) Arrays.sort(column);
            return new Template(width, height, depth, palette, columns);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load city structure " + id, e);
        }
    }

    private record Template(int width, int height, int depth, BlockState[] palette, int[][] columns) {
        BlockState get(int x, int y, int z) {
            if (x < 0 || x >= width || z < 0 || z >= depth || y < 0 || y >= height) return null;
            int[] entries = columns[z * width + x];
            if (entries == null) return null;
            int i = Arrays.binarySearch(entries, y << 8);
            if (i < 0) i = -i - 1;
            return i < entries.length && (entries[i] >>> 8) == y ? palette[entries[i] & 255] : null;
        }
    }
}
