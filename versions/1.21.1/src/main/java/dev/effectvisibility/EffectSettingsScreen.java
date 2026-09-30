package dev.effectvisibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

/** Edits a draft; Escape, Cancel and resizing never write configuration. */
public final class EffectSettingsScreen extends Screen {
    private final @Nullable Screen parent;
    private final Set<String> hidden;
    private boolean hideAll;
    private Button hideAllButton;
    private String query = "";
    private int page;
    private int rowsPerPage;
    private EditBox search;
    private Button previous, next;
    private final List<Button> effectButtons = new ArrayList<>();
    private List<ResourceLocation> matches = List.of();

    public EffectSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("effect_visibility.title"));
        this.parent = parent;
        var rules = ClientConfig.rules();
        hidden = new HashSet<>(rules.hiddenEffects());
        hideAll = rules.hideAll();
    }

    @Override
    protected void init() {
        effectButtons.clear();
        int w = Math.min(460, width - 20);
        int x = (width - w) / 2;
        int half = (w - 4) / 2;
        hideAllButton = addRenderableWidget(Button.builder(toggle("hide_all", hideAll), b -> {
            hideAll = !hideAll; b.setMessage(toggle("hide_all", hideAll)); refreshRows();
        }).bounds(x, 42, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("effect_visibility.reset"), b -> {
            hideAll = false;
            hidden.clear();
            hideAllButton.setMessage(toggle("hide_all", false));
            refreshRows();
        }).bounds(x + half + 4, 42, w - half - 4, 20).build());
        search = addRenderableWidget(new EditBox(font, x, 70, w, 20, Component.translatable("effect_visibility.search")));
        search.setMaxLength(128);
        search.setHint(Component.translatable("effect_visibility.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; refreshRows(); });
        rowsPerPage = Math.max(1, (height - 156) / 24);
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refreshRows(); })
                .bounds(x, height - 54, 40, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refreshRows(); })
                .bounds(x + w - 40, height - 54, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("effect_visibility.save"), b -> {
            ClientConfig.save(new VisibilityRules(hideAll, hidden)); onClose();
        }).bounds(x, height - 28, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + half + 4, height - 28, w - half - 4, 20).build());
        refreshRows();
    }

    private Component toggle(String key, boolean value) {
        return Component.translatable("effect_visibility." + key,
                Component.translatable(value ? "options.on" : "options.off"));
    }

    private void refreshRows() {
        effectButtons.forEach(this::removeWidget);
        effectButtons.clear();
        String lower = query.strip().toLowerCase(Locale.ROOT);
        matches = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .filter(id -> EffectPickerPolicy.isSelectable(id.toString()))
                .filter(id -> id.toString().contains(lower) || name(id).getString().toLowerCase(Locale.ROOT).contains(lower))
                .sorted(Comparator.comparing((ResourceLocation id) -> name(id).getString()).thenComparing(ResourceLocation::toString))
                .toList();
        int pages = Math.max(1, (matches.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = page > 0;
        next.active = page + 1 < pages;
        int w = Math.min(460, width - 20);
        int x = (width - w) / 2;
        for (int row = 0; row < rowsPerPage && page * rowsPerPage + row < matches.size(); row++) {
            ResourceLocation id = matches.get(page * rowsPerPage + row);
            String key = id.toString();
            boolean selected = hideAll || hidden.contains(key);
            Button button = Button.builder(effectLabel(id, selected), b -> {
                if (!hidden.remove(key)) hidden.add(key);
                b.setMessage(effectLabel(id, hidden.contains(key)));
            }).bounds(x, 98 + row * 24, w, 20)
                    .tooltip(Tooltip.create(hideAll ? Component.translatable("effect_visibility.all_tip") : Component.literal(key))).build();
            button.active = !hideAll;
            effectButtons.add(addRenderableWidget(button));
        }
    }

    private Component name(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.get(id).getDisplayName();
    }

    private Component effectLabel(ResourceLocation id, boolean selected) {
        return Component.translatable(selected ? "effect_visibility.hidden" : "effect_visibility.visible", name(id));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, hideAll ? Component.translatable("effect_visibility.all_summary")
                : Component.translatable("effect_visibility.summary", hidden.size()), width / 2, 28, 0xFFBBBBBB);
        if (matches.isEmpty()) graphics.drawCenteredString(font, Component.translatable("effect_visibility.empty"), width / 2, 102, 0xFFBBBBBB);
        int pages = Math.max(1, (matches.size() + rowsPerPage - 1) / rowsPerPage);
        graphics.drawCenteredString(font, Component.translatable("effect_visibility.page", page + 1, pages, matches.size()),
                width / 2, height - 48, 0xFFFFFFFF);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
