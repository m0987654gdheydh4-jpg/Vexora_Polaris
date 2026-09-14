package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.module.impl.player.AutoBuy;
import polaris.utils.modules.autobuy.AuctionUtils;


@Mixin(AbstractContainerScreen.class)
public abstract class AuctionContainerMixin extends Screen {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;

    @Unique
    private Button cataclysm$autoBuyBtn;
    @Unique
    private Button cataclysm$modeBtn;
    @Unique
    private Button cataclysm$autoParseBtn;
    @Unique
    private Button cataclysm$parseNowBtn;
    @Unique
    private Button cataclysm$discountBtn;
    @Unique
    private Button cataclysm$refreshBtn;
    @Unique
    private Button cataclysm$anarchyBtn;

    protected AuctionContainerMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cataclysm$initAuctionButtons(CallbackInfo ci) {
        if (!((Object) this instanceof ContainerScreen screen)) {
            return;
        }
        if (!cataclysm$isAuction(screen)) {
            return;
        }

        AutoBuy ab = AutoBuy.getInstance();
        if (ab == null) {
            return;
        }

        int btnW = 110;
        int btnH = 20;
        int autoBuyX = leftPos + imageWidth / 2 - btnW / 2;
        int autoBuyY = topPos - btnH - 5;
        if (autoBuyY < 4) {
            autoBuyY = topPos + imageHeight + 4;
        }

        cataclysm$autoBuyBtn = Button.builder(cataclysm$autoBuyLabel(ab), b -> {
            ab.toggle();
            b.setMessage(cataclysm$autoBuyLabel(ab));
        }).bounds(autoBuyX, autoBuyY, btnW, btnH).build();
        this.addRenderableWidget(cataclysm$autoBuyBtn);

        cataclysm$modeBtn = Button.builder(cataclysm$modeLabel(ab), b -> {
            ab.cycleServerMode();
            b.setMessage(cataclysm$modeLabel(ab));
        }).bounds(autoBuyX, autoBuyY - 24, btnW, btnH).build();
        this.addRenderableWidget(cataclysm$modeBtn);

        int sideX = leftPos - 100;
        if (sideX < 4) {
            sideX = leftPos + imageWidth + 4;
        }
        int sideY = topPos;
        int sideW = 95;

        cataclysm$autoParseBtn = Button.builder(cataclysm$parseLabel(ab), b -> {
            ab.toggleAutoParse();
            b.setMessage(cataclysm$parseLabel(ab));
        }).bounds(sideX, sideY, sideW, btnH).build();
        this.addRenderableWidget(cataclysm$autoParseBtn);

        cataclysm$discountBtn = Button.builder(cataclysm$discountLabel(ab), b -> {
            
            int d = ab.getParseDiscountSetting().getValue().intValue() + 5;
            if (d > 100) {
                d = 5;
            }
            ab.getParseDiscountSetting().setValue((double) d);
            b.setMessage(cataclysm$discountLabel(ab));
        }).bounds(sideX, sideY + 24, sideW, btnH).build();
        this.addRenderableWidget(cataclysm$discountBtn);

        cataclysm$refreshBtn = Button.builder(cataclysm$refreshLabel(ab), b -> {
            int v = ab.getUpdateDelaySetting().getValue().intValue() + 50;
            if (v > 2000) {
                v = 150;
            }
            ab.getUpdateDelaySetting().setValue((double) v);
            b.setMessage(cataclysm$refreshLabel(ab));
        }).bounds(sideX, sideY + 48, sideW, btnH).build();
        this.addRenderableWidget(cataclysm$refreshBtn);

        cataclysm$parseNowBtn = Button.builder(Component.literal("Парс сейчас"), b -> ab.startParseNow())
                .bounds(sideX, sideY + 72, sideW, btnH).build();
        this.addRenderableWidget(cataclysm$parseNowBtn);

        cataclysm$anarchyBtn = Button.builder(cataclysm$anarchyLabel(ab), b -> {
            if (ab.getServerMode().is("FunTime")) {
                int min = ab.getAnarchyMinSecSetting().getValue().intValue() + 5;
                int max = ab.getAnarchyMaxSecSetting().getValue().intValue() + 5;
                if (min > 300) {
                    min = 90;
                    max = 120;
                }
                ab.getAnarchyMinSecSetting().setValue((double) min);
                ab.getAnarchyMaxSecSetting().setValue((double) Math.max(min + 5, max));
            } else if (ab.getServerMode().is("SpookyTime")) {
                ab.getSpWalkSetting().setValue(!ab.getSpWalkSetting().getValue());
            } else {
                
                int v = ab.getUpdateDelaySetting().getValue().intValue() + 50;
                if (v > 450) {
                    v = 100;
                }
                ab.getUpdateDelaySetting().setValue((double) v);
            }
            b.setMessage(cataclysm$anarchyLabel(ab));
        }).bounds(sideX, sideY + 96, sideW, btnH).build();
        this.addRenderableWidget(cataclysm$anarchyBtn);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cataclysm$updateAuctionButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof ContainerScreen screen)) {
            return;
        }
        boolean auction = cataclysm$isAuction(screen);
        AutoBuy ab = AutoBuy.getInstance();
        boolean show = auction && ab != null;

        updateBtn(cataclysm$autoBuyBtn, show, show ? cataclysm$autoBuyLabel(ab) : null);
        updateBtn(cataclysm$modeBtn, show, show ? cataclysm$modeLabel(ab) : null);
        updateBtn(cataclysm$autoParseBtn, show, show ? cataclysm$parseLabel(ab) : null);
        updateBtn(cataclysm$discountBtn, show, show ? cataclysm$discountLabel(ab) : null);
        updateBtn(cataclysm$refreshBtn, show, show ? cataclysm$refreshLabel(ab) : null);
        updateBtn(cataclysm$anarchyBtn, show, show ? cataclysm$anarchyLabel(ab) : null);
        if (cataclysm$parseNowBtn != null) {
            cataclysm$parseNowBtn.visible = show;
            cataclysm$parseNowBtn.active = show && ab != null && !ab.isParseRunning();
            if (show) {
                cataclysm$parseNowBtn.setMessage(Component.literal(
                        ab.isParseRunning() ? "Парсинг…" : "Парс сейчас"));
            }
        }
    }

    @Unique
    private void updateBtn(Button btn, boolean show, Component msg) {
        if (btn == null) {
            return;
        }
        btn.visible = show;
        btn.active = show;
        if (show && msg != null) {
            btn.setMessage(msg);
        }
    }

    @Unique
    private boolean cataclysm$isAuction(ContainerScreen screen) {
        String title = screen.getTitle().getString();
        if (AuctionUtils.isAuctionTitle(title) || AuctionUtils.isSearchTitle(title)) {
            return true;
        }
        if (title != null && (title.contains("Поиск") || title.contains("Search") || title.contains("Аукцион"))) {
            return true;
        }
        
        int n = Math.min(54, screen.getMenu().slots.size());
        for (int i = 0; i < n; i++) {
            if (AuctionUtils.getPrice(screen.getMenu().slots.get(i).getItem()) > 0) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private Component cataclysm$autoBuyLabel(AutoBuy ab) {
        return Component.literal("AutoBuy: " + (ab.isEnabled() ? "§aON" : "§cOFF"));
    }

    @Unique
    private Component cataclysm$modeLabel(AutoBuy ab) {
        return Component.literal("Mode: " + ab.getServerMode().getValue());
    }

    @Unique
    private Component cataclysm$parseLabel(AutoBuy ab) {
        return Component.literal("AutoParse: " + (ab.isAutoParseEnabled() ? "§aON" : "§cOFF"));
    }

    @Unique
    private Component cataclysm$discountLabel(AutoBuy ab) {
        return Component.literal("Скидка: " + ab.getParseDiscountSetting().getValue().intValue() + "%");
    }

    @Unique
    private Component cataclysm$refreshLabel(AutoBuy ab) {
        return Component.literal("Обнов: " + ab.getUpdateDelaySetting().getValue().intValue() + "ms");
    }

    @Unique
    private Component cataclysm$anarchyLabel(AutoBuy ab) {
        if (ab.getServerMode().is("FunTime")) {
            return Component.literal("FT /an: "
                    + ab.getAnarchyMinSecSetting().getValue().intValue() + "-"
                    + ab.getAnarchyMaxSecSetting().getValue().intValue() + "s");
        }
        if (ab.getServerMode().is("SpookyTime")) {
            return Component.literal("SP walk: "
                    + (ab.getSpWalkSetting().getValue() ? "§aON" : "§cOFF")
                    + " " + ab.getSpWalkBlocksSetting().getValue().intValue() + "b");
        }
        return Component.literal("HW · unit buy");
    }
}
