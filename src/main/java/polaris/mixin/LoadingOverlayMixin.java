package polaris.mixin; // Убедись, что этот пакет совпадает с твоей папкой mixin

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.packs.resources.ReloadInstance;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    @Shadow @Final private ReloadInstance reload;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    // ИСПРАВЛЕНИЕ ЗАДЕРЖКИ: Снимаем ванильный блок Майнкрафта на отрисовку шрифтов.
    // Теперь игра разрешает рендерить текст и проценты с самой первой милисекунды запуска.
    @Inject(method = "isReadyToFadeOut", at = @At("HEAD"), cancellable = true)
    private void onIsReadyToFadeOut(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        // 1. Заливаем весь экран темно-серым фоном Polaris (0xFF0C0C0C)
        int backgroundColor = ARGB.color(255, 12, 12, 12);
        guiGraphics.fill(0, 0, width, height, backgroundColor);

        int centerX = width / 2;
        int centerY = height / 2;

        // Вычисление альфа-канала для плавного исчезновения заставки при переходе в меню
        int alphaValue = 255;
        float g = this.fadeOutStart > -1L ? (float)(net.minecraft.util.Util.getMillis() - this.fadeOutStart) / 1000.0F : -1.0F;
        if (g >= 1.0F) {
            float fadeAlpha = 1.0F - Mth.clamp(g - 1.0F, 0.0F, 1.0F);
            alphaValue = Math.round(fadeAlpha * 255.0F);
        }

        // Обновляем прогресс каждую миллисекунду, чтобы полоса и проценты шли плавно
        float actualSpeed = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actualSpeed * 0.050000012F, 0.0F, 1.0F);

        // 2. ОДНОВРЕМЕННАЯ ОТРИСОВКА ГИГАНТСКОГО ЛОГОТИПА VEXORA
        var font = Minecraft.getInstance().font;
        String firstLetter = "V";
        String remainingText = "exora";

        int vWidth = font.width(firstLetter);
        int totalWidth = font.width(firstLetter + remainingText);

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) centerX, (float) (centerY - 40));
        guiGraphics.pose().scale(6.0F, 6.0F); // Огромный масштаб логотипа 6x

        float startX = -(totalWidth / 2.0F);

        // Синяя буква "V"
        int blueColor = ARGB.color(alphaValue, 0, 120, 255);
        guiGraphics.drawString(font, firstLetter, (int) startX, 0, blueColor, false);

        // Белая часть "exora"
        int whiteColor = ARGB.color(alphaValue, 255, 255, 255);
        guiGraphics.drawString(font, remainingText, (int) (startX + vWidth), 0, whiteColor, false);

        guiGraphics.pose().popMatrix();

        // 3. ОТРИСОВКА СИНХРОННЫХ ПРОЦЕНТОВ И УВЕЛИЧЕННОГО ПРОГРЕСС-БАРА
        int progressY = (int)((double)height * 0.82D);

        // Проценты теперь жестко рендерятся вместе со всем интерфейсом с самого старта
        String progressPercent = Math.round(this.currentProgress * 100.0F) + "%";
        int percentColor = ARGB.color(alphaValue, 200, 200, 200);
        int percentWidth = font.width(progressPercent);
        guiGraphics.drawString(font, progressPercent, centerX - (percentWidth / 2), progressY - 20, percentColor, false);

        if (g < 1.0F) {
            // УВЕЛИЧЕННЫЙ ПРОГРЕСС-ВАР (Ширина 450px, высота 12px)
            int barWidth = 450;
            int barHeight = 12;
            int xStart = centerX - (barWidth / 2);
            int yStart = progressY;

            int fillWidth = Mth.ceil((float)barWidth * this.currentProgress);

            int bgOutlineColor = ARGB.color(alphaValue, 45, 45, 45);
            int bgTrackColor = ARGB.color(alphaValue, 15, 15, 15);
            int clientFillColor = ARGB.color(alphaValue, 0, 120, 255); // Фирменный синий Polaris

            // Отрисовка геометрии полосы загрузки
            guiGraphics.fill(xStart - 1, yStart - 1, xStart + barWidth + 1, yStart + barHeight + 1, bgOutlineColor);
            guiGraphics.fill(xStart, yStart, xStart + barWidth, yStart + barHeight, bgTrackColor);
            if (fillWidth > 0) {
                guiGraphics.fill(xStart, yStart, xStart + fillWidth, yStart + barHeight, clientFillColor);
            }
        }

        if (g >= 2.0F) {
            Minecraft.getInstance().setOverlay(null);
        }

        ci.cancel();
    }
}
