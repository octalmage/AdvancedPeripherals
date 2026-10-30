package de.srendi.advancedperipherals.common.addons.create;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectLuaTable;
import de.srendi.advancedperipherals.common.util.Pair;
import de.srendi.advancedperipherals.lib.peripherals.APGenericPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class FilteringBehaviourIntegration implements APGenericPeripheral {
    @Override
    @NotNull
    public String getPeripheralType() {
        return "filtering_behaviour";
    }

    @LuaFunction(mainThread = true)
    public final Map<String, ?> getFilter(Wrapper wrapper, Optional<String> face) throws LuaException {
        FilteringBehaviour behaviour = selectBehaviour(wrapper, face);
        Map<String, Object> data = CreateFilter.filterToLua(FilterItemStack.of(behaviour.getFilter()), wrapper.blockEntity().getLevel().registryAccess());
        if (data == null) {
            return null;
        }
        data = new HashMap<>(data);
        data.put("count", behaviour.count);
        data.put("upTo", behaviour.upTo);
        return data;
    }

    @LuaFunction(mainThread = true)
    public final MethodResult setFilter(Wrapper wrapper, Optional<Map<?, ?>> filter, Optional<String> face) throws LuaException {
        FilteringBehaviour behaviour = selectBehaviour(wrapper, face);
        ItemStack filterStack = behaviour.getFilter();
        LuaTable<?, ?> filterTable = filter.map(ObjectLuaTable::new).orElse(null);

        Pair<ItemStack, String> result = CreateFilter.updateFilter(filterStack, filterTable, wrapper.blockEntity().getLevel().registryAccess());
        if (result.rightPresent()) {
            return MethodResult.of(false, result.right());
        }
        if (!behaviour.setFilter(result.left())) {
            return MethodResult.of(false, "invalid filter");
        }
        if (filterTable != null) {
            int maxStackSize = behaviour.getMaxStackSize();
            int count = Math.min(Math.max(filterTable.optInt("count").orElse(maxStackSize), 1), maxStackSize);
            boolean upTo = filterTable.optBoolean("upTo").orElse(true);
            if (behaviour.count != count || behaviour.upTo != upTo) {
                behaviour.count = count;
                behaviour.upTo = upTo;
                wrapper.blockEntity().notifyUpdate();
            }
        }
        return MethodResult.of(true);
    }

    private static FilteringBehaviour selectBehaviour(Wrapper wrapper, Optional<String> face) throws LuaException {
        FilteringBehaviour behaviour = wrapper.behaviour();
        if (face.isEmpty()) {
            if (behaviour instanceof SidedFilteringBehaviour) {
                throw new LuaException("face is required for sided filtering");
            }
            return behaviour;
        }

        Direction direction = Direction.byName(face.get().toLowerCase(Locale.ROOT));
        if (direction == null) {
            throw new LuaException("invalid face: " + face.get() + " (tunnel faces: north, south, east, west)");
        }
        if (behaviour instanceof SidedFilteringBehaviour sided) {
            FilteringBehaviour selected = sided.get(direction);
            if (selected == null) {
                throw new LuaException("no filter port on face: " + direction.getSerializedName());
            }
            return selected;
        }
        return behaviour;
    }

    public record Wrapper(FilteringBehaviour behaviour, SmartBlockEntity blockEntity) {
    }
}
