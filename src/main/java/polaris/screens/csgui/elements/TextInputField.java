package polaris.screens.csgui.elements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class TextInputField {
    public static TextInputField focusedField;

    private static final FontType FONT = FontType.INTER_MEDIUM;
    private static final float LINE_HEIGHT_MULT = 1.4f;
    private static final float BASELINE_OFFSET_MULT = 0.36f;
    private static final float CURSOR_WIDTH = 1.5f;
    private static final float PADDING = 4f;
    private static final int MAX_UNDO = 100;
    private static final long BLINK_INTERVAL = 500L;

    private float x, y, width, height, radius;
    private float textSize;
    private String placeholder;
    private boolean multiline;
    private boolean editable;
    private boolean focused;
    private int textColor;
    private int cursorColor;
    private int selectionColor;
    private int bgColor;
    private int placeholderColor;
    private Predicate<Character> charFilter;
    private Consumer<String> changeCallback;
    private IntConsumer enterCallback;

    private final StringBuilder text;
    private int cursorPos;
    private int selectionAnchor;
    private float scrollX, scrollY;
    private long lastBlinkTime;
    private boolean cursorVisible;
    private final Deque<String> undoStack;
    private final Deque<String> redoStack;
    private boolean suppressNextChar;
    private float preferredCursorX;
    private boolean selecting;

    public TextInputField(float x, float y, float width, float height) {
        this(x, y, width, height, 0f);
    }

    public TextInputField(float x, float y, float width, float height, float radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.textSize = 12f;
        this.text = new StringBuilder();
        this.cursorPos = 0;
        this.selectionAnchor = -1;
        this.editable = true;
        this.multiline = false;
        this.textColor = ColorUtil.WHITE;
        this.cursorColor = ColorUtil.WHITE;
        this.selectionColor = ColorUtil.rgba(60, 120, 255, 80);
        this.bgColor = ColorUtil.rgba(30, 30, 30, 200);
        this.placeholderColor = ColorUtil.rgba(128, 128, 128, 200);
        this.undoStack = new ArrayDeque<>();
        this.redoStack = new ArrayDeque<>();
        this.placeholder = "";
        this.lastBlinkTime = System.currentTimeMillis();
        this.cursorVisible = true;
        this.preferredCursorX = -1f;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    public void setTextSize(float size) { this.textSize = size; }
    public float getTextSize() { return textSize; }

    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    public String getPlaceholder() { return placeholder; }

    public void setMultiline(boolean multiline) { this.multiline = multiline; }
    public boolean isMultiline() { return multiline; }

    public void setEditable(boolean editable) { this.editable = editable; }
    public boolean isEditable() { return editable; }

    public void setTextColor(int color) { this.textColor = color; }
    public void setCursorColor(int color) { this.cursorColor = color; }
    public void setSelectionColor(int color) { this.selectionColor = color; }
    public void setBackgroundColor(int color) { this.bgColor = color; }
    public void setPlaceholderColor(int color) { this.placeholderColor = color; }

    public void setCharFilter(Predicate<Character> filter) { this.charFilter = filter; }
    public void setChangeCallback(Consumer<String> callback) { this.changeCallback = callback; }
    public void setEnterCallback(IntConsumer callback) { this.enterCallback = callback; }

    public String getText() { return text.toString(); }

    public void setText(String text) {
        this.text.setLength(0);
        this.text.append(text);
        this.cursorPos = clampCursor(this.text.length());
        this.selectionAnchor = -1;
        this.scrollX = 0;
        this.scrollY = 0;
        this.undoStack.clear();
        this.redoStack.clear();
        this.preferredCursorX = -1f;
        onChanged();
    }

    public void clear() {
        setText("");
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        if (this.focused == focused) return;
        this.focused = focused;
        if (focused) {
            focusedField = this;
            cursorVisible = true;
            lastBlinkTime = System.currentTimeMillis();
            preferredCursorX = -1f;
        } else {
            if (focusedField == this) {
                focusedField = null;
            }
        }
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render(GuiGraphics graphics) {
        
        int bgA = (bgColor >>> 24) & 0xFF;
        if (bgA > 8) {
            Render2D.rect(x, y, width, height, radius, bgColor);
        }

        float padX = Math.max(PADDING, 6f);
        float padY = Math.max(2f, Math.min(PADDING, (height - textSize) * 0.5f));
        float innerX = x + padX;
        float innerY = y + padY;
        float innerW = Math.max(4f, width - padX * 2f);
        float innerH = Math.max(4f, height - padY * 2f);

        Render2D.pushScissor(graphics, innerX - 1f, y + 1f, innerW + 2f, height - 2f);

        float lineHeight = multiline ? getLineHeight() : Math.min(getLineHeight(), innerH);
        int lineCount = getLineCount();

        ensureCursorVisible();

        
        float textY0 = multiline
                ? innerY - scrollY + textSize * 0.05f
                : y + (height - textSize) * 0.5f;

        if (text.length() == 0 && !focused && placeholder != null && !placeholder.isEmpty()) {
            Render2D.text(FONT, placeholder, innerX - scrollX, textY0, textSize, placeholderColor);
        }

        if (hasSelection()) {
            int selStart = Math.min(cursorPos, selectionAnchor);
            int selEnd = Math.max(cursorPos, selectionAnchor);
            for (int line = 0; line < lineCount; line++) {
                int lineStart = getLineStart(line);
                int lineEnd = getLineEnd(line);
                if (selStart >= lineEnd || selEnd <= lineStart) continue;

                float ly = multiline
                        ? innerY + line * lineHeight - scrollY
                        : y + (height - lineHeight) * 0.5f;
                String lineStr = getLineText(line);
                int localStart = Math.max(0, selStart - lineStart);
                int localEnd = Math.min(lineStr.length(), selEnd - lineStart);

                float startX = innerX - scrollX + Render2D.textWidth(FONT, lineStr.substring(0, localStart), textSize);
                float endX = innerX - scrollX + Render2D.textWidth(FONT, lineStr.substring(0, localEnd), textSize);
                float rx = Math.min(startX, endX);
                float rw = Math.abs(endX - startX);
                if (rw > 0.5f) {
                    Render2D.rect(rx, ly, rw, lineHeight, 0, selectionColor);
                }
            }
        }

        for (int line = 0; line < lineCount; line++) {
            String lineStr = getLineText(line);
            if (lineStr.isEmpty()) continue;
            float lx = innerX - scrollX;
            float ly = multiline
                    ? innerY + line * lineHeight - scrollY + textSize * 0.05f
                    : textY0;
            Render2D.text(FONT, lineStr, lx, ly, textSize, textColor);
        }

        if (focused) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkTime > BLINK_INTERVAL) {
                cursorVisible = !cursorVisible;
                lastBlinkTime = now;
            }
            if (cursorVisible) {
                int line = getCursorLine();
                String lineStr = getLineText(line);
                int localOffset = Math.max(0, Math.min(cursorPos - getLineStart(line), lineStr.length()));
                String linePrefix = lineStr.substring(0, localOffset);
                float cx = innerX - scrollX + Render2D.textWidth(FONT, linePrefix, textSize);
                float cy = multiline
                        ? innerY + line * lineHeight - scrollY
                        : y + (height - Math.min(lineHeight, height - 4f)) * 0.5f;
                float ch = multiline ? lineHeight : Math.min(lineHeight, height - 4f);
                Render2D.rect(cx, cy, CURSOR_WIDTH, ch, 0, cursorColor);
            }
        }

        Render2D.popScissor(graphics);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        setFocused(true);
        if (button == 0) {
            cursorPos = getPosAt((float) mouseX, (float) mouseY);
            selectionAnchor = -1;
            resetCursorBlink();
            preferredCursorX = -1f;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!focused || button != 0) return false;
        if (!selecting) {
            selectionAnchor = cursorPos;
            selecting = true;
        }
        float clampedX = (float) Math.max(x + PADDING, Math.min(x + width - PADDING, mouseX));
        float clampedY = multiline
                ? (float) Math.max(y + PADDING, Math.min(y + height - PADDING, mouseY))
                : (float) mouseY;
        cursorPos = getPosAt(clampedX, clampedY);
        resetCursorBlink();
        ensureCursorVisible();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && selecting) {
            if (cursorPos == selectionAnchor) {
                selectionAnchor = -1;
            }
            selecting = false;
            return focused;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!focused || !multiline || !isMouseOver(mouseX, mouseY)) return false;
        float totalH = getLineCount() * getLineHeight();
        float innerH = height - PADDING * 2;
        float maxScroll = Math.max(0, totalH - innerH);
        scrollY = (float) Math.max(0, Math.min(maxScroll, scrollY - delta * getLineHeight() * 3));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !editable) return false;

        long window = GLFW.glfwGetCurrentContext();
        boolean control = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean handled = true;

        if (control) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_C -> copy();
                case GLFW.GLFW_KEY_V -> paste();
                case GLFW.GLFW_KEY_X -> cut();
                case GLFW.GLFW_KEY_Z -> undo();
                case GLFW.GLFW_KEY_Y -> redo();
                case GLFW.GLFW_KEY_A -> selectAll();
                default -> handled = false;
            }
        } else {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> moveCursorHorizontal(-1, shift);
                case GLFW.GLFW_KEY_RIGHT -> moveCursorHorizontal(1, shift);
                case GLFW.GLFW_KEY_UP -> {
                    if (multiline) moveCursorVertical(-1, shift);
                    else handled = false;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    if (multiline) moveCursorVertical(1, shift);
                    else handled = false;
                }
                case GLFW.GLFW_KEY_HOME -> moveCursorHome(shift);
                case GLFW.GLFW_KEY_END -> moveCursorEnd(shift);
                case GLFW.GLFW_KEY_BACKSPACE -> backspace();
                case GLFW.GLFW_KEY_DELETE -> deleteForward();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (multiline) {
                        insertChar('\n');
                    } else if (enterCallback != null) {
                        enterCallback.accept(getLineCount());
                    }
                    suppressNextChar = true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> setFocused(false);
                default -> handled = false;
            }
        }

        if (control && (keyCode == GLFW.GLFW_KEY_V || keyCode == GLFW.GLFW_KEY_C
                || keyCode == GLFW.GLFW_KEY_X || keyCode == GLFW.GLFW_KEY_Z
                || keyCode == GLFW.GLFW_KEY_Y)) {
            suppressNextChar = true;
        }

        return handled;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!focused || !editable) return false;
        if (suppressNextChar) {
            suppressNextChar = false;
            return true;
        }
        if (codePoint == '\r' || codePoint == '\n') return true;
        if (charFilter != null && !charFilter.test(codePoint)) return false;
        insertChar(codePoint);
        return true;
    }

    private void insertChar(char ch) {
        if (hasSelection()) {
            pushUndo();
            deleteSelectionInternal();
        } else {
            pushUndo();
        }
        text.insert(cursorPos, ch);
        cursorPos++;
        preferredCursorX = -1f;
        resetCursorBlink();
        onChanged();
    }

    private void backspace() {
        if (hasSelection()) {
            pushUndo();
            deleteSelectionInternal();
            return;
        }
        if (cursorPos <= 0) return;
        pushUndo();
        text.deleteCharAt(cursorPos - 1);
        cursorPos--;
        preferredCursorX = -1f;
        resetCursorBlink();
        onChanged();
    }

    private void deleteForward() {
        if (hasSelection()) {
            pushUndo();
            deleteSelectionInternal();
            return;
        }
        if (cursorPos >= text.length()) return;
        pushUndo();
        text.deleteCharAt(cursorPos);
        resetCursorBlink();
        onChanged();
    }

    private void deleteSelectionInternal() {
        int start = Math.min(cursorPos, selectionAnchor);
        int end = Math.max(cursorPos, selectionAnchor);
        text.delete(start, end);
        cursorPos = start;
        selectionAnchor = -1;
        preferredCursorX = -1f;
    }

    private void moveCursorHorizontal(int dir, boolean shift) {
        if (shift) {
            if (!selecting) {
                selectionAnchor = cursorPos;
                selecting = true;
            }
        } else {
            selecting = false;
            selectionAnchor = -1;
        }
        int newPos = clampCursor(cursorPos + dir);
        if (newPos != cursorPos) {
            cursorPos = newPos;
            preferredCursorX = -1f;
        }
        resetCursorBlink();
    }

    private void moveCursorVertical(int dir, boolean shift) {
        if (!multiline) return;

        if (shift) {
            if (!selecting) {
                selectionAnchor = cursorPos;
                selecting = true;
            }
        } else {
            selecting = false;
            selectionAnchor = -1;
        }

        int curLine = getCursorLine();
        int targetLine = Math.max(0, Math.min(getLineCount() - 1, curLine + dir));
        if (targetLine == curLine) return;

        if (preferredCursorX < 0) {
            String curLineStr = getLineText(curLine);
            int localOff = Math.max(0, Math.min(cursorPos - getLineStart(curLine), curLineStr.length()));
            preferredCursorX = Render2D.textWidth(FONT, curLineStr.substring(0, localOff), textSize);
        }

        int lineStart = getLineStart(targetLine);
        int lineEnd = getLineEnd(targetLine);
        String targetStr = text.substring(lineStart, lineEnd);

        int localPos = findCharIndexAtWidth(targetStr, preferredCursorX);
        cursorPos = lineStart + localPos;
        resetCursorBlink();
    }

    private void moveCursorHome(boolean shift) {
        if (shift) {
            if (!selecting) {
                selectionAnchor = cursorPos;
                selecting = true;
            }
        } else {
            selecting = false;
            selectionAnchor = -1;
        }
        cursorPos = getLineStart(getCursorLine());
        preferredCursorX = 0;
        resetCursorBlink();
    }

    private void moveCursorEnd(boolean shift) {
        if (shift) {
            if (!selecting) {
                selectionAnchor = cursorPos;
                selecting = true;
            }
        } else {
            selecting = false;
            selectionAnchor = -1;
        }
        int line = getCursorLine();
        cursorPos = getLineEnd(line);
        String lineStr = getLineText(line);
        preferredCursorX = Render2D.textWidth(FONT, lineStr, textSize);
        resetCursorBlink();
    }

    private void selectAll() {
        selectionAnchor = 0;
        cursorPos = text.length();
        selecting = true;
        resetCursorBlink();
    }

    private void copy() {
        if (!hasSelection()) return;
        String selected = text.substring(Math.min(cursorPos, selectionAnchor), Math.max(cursorPos, selectionAnchor));
        Minecraft.getInstance().keyboardHandler.setClipboard(selected);
    }

    private void cut() {
        if (!hasSelection()) return;
        copy();
        pushUndo();
        deleteSelectionInternal();
        preferredCursorX = -1f;
        onChanged();
    }

    private void paste() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;

        String toInsert = clip;
        if (!multiline) {
            toInsert = clip.replace("\r\n", "").replace('\r', ' ').replace('\n', ' ');
        } else {
            toInsert = clip.replace("\r\n", "\n").replace('\r', '\n');
        }
        if (toInsert.isEmpty()) return;

        if (hasSelection()) {
            pushUndo();
            deleteSelectionInternal();
        } else {
            pushUndo();
        }

        text.insert(cursorPos, toInsert);
        cursorPos += toInsert.length();
        preferredCursorX = -1f;
        resetCursorBlink();
        onChanged();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        if (!hasSelection()) {
            selectionAnchor = -1;
        }
        redoStack.addLast(text.toString());
        if (redoStack.size() > MAX_UNDO) redoStack.removeFirst();
        String prev = undoStack.removeLast();
        text.setLength(0);
        text.append(prev);
        cursorPos = clampCursor(cursorPos);
        selectionAnchor = -1;
        preferredCursorX = -1f;
        onChanged();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.addLast(text.toString());
        if (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
        String next = redoStack.removeLast();
        text.setLength(0);
        text.append(next);
        cursorPos = clampCursor(cursorPos);
        selectionAnchor = -1;
        preferredCursorX = -1f;
        onChanged();
    }

    private void pushUndo() {
        if (undoStack.isEmpty() || !undoStack.peekLast().equals(text.toString())) {
            undoStack.addLast(text.toString());
            if (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
        }
        redoStack.clear();
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursorPos;
    }

    private int clampCursor(int pos) {
        return Math.max(0, Math.min(text.length(), pos));
    }

    private void resetCursorBlink() {
        cursorVisible = true;
        lastBlinkTime = System.currentTimeMillis();
    }

    private void onChanged() {
        if (changeCallback != null) {
            changeCallback.accept(text.toString());
        }
    }

    private int getPosAt(float mx, float my) {
        float lineHeight = getLineHeight();
        float innerX = x + PADDING - scrollX;
        float innerY = y + PADDING - scrollY;
        int lineCount = getLineCount();

        int line = (int) ((my - innerY) / lineHeight);
        line = Math.max(0, Math.min(lineCount - 1, line));

        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        if (lineStart >= lineEnd) return lineStart;

        String lineStr = text.substring(lineStart, lineEnd);
        float relX = mx - innerX;

        if (relX <= 0) return lineStart;

        float totalWidth = Render2D.textWidth(FONT, lineStr, textSize);
        if (relX >= totalWidth) return lineEnd;

        int low = 0;
        int high = lineStr.length();
        while (low < high) {
            int mid = (low + high) >>> 1;
            float w = Render2D.textWidth(FONT, lineStr.substring(0, mid), textSize);
            if (w < relX) low = mid + 1;
            else high = mid;
        }
        return lineStart + low;
    }

    private int findCharIndexAtWidth(String str, float targetWidth) {
        if (str == null || str.isEmpty()) return 0;
        if (targetWidth <= 0) return 0;
        float totalWidth = Render2D.textWidth(FONT, str, textSize);
        if (targetWidth >= totalWidth) return str.length();

        int low = 0;
        int high = str.length();
        while (low < high) {
            int mid = (low + high) >>> 1;
            float w = Render2D.textWidth(FONT, str.substring(0, mid), textSize);
            if (w < targetWidth) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private void ensureCursorVisible() {
        float lineHeight = getLineHeight();
        float innerX = x + PADDING;
        float innerY = y + PADDING;
        float innerW = width - PADDING * 2;
        float innerH = height - PADDING * 2;

        int line = getCursorLine();
        int lineStart = getLineStart(line);
        String lineStr = getLineText(line);
        int localOffset = Math.max(0, Math.min(cursorPos - lineStart, lineStr.length()));
        String linePrefix = lineStr.substring(0, localOffset);

        float cursorRelX = Render2D.textWidth(FONT, linePrefix, textSize);
        float cursorRelY = line * lineHeight;

        float cursorAbsX = innerX - scrollX + cursorRelX;
        if (cursorAbsX < innerX) {
            scrollX += innerX - cursorAbsX;
        } else if (cursorAbsX + CURSOR_WIDTH > innerX + innerW) {
            scrollX -= cursorAbsX + CURSOR_WIDTH - (innerX + innerW);
        }

        if (multiline) {
            float cursorAbsY = innerY - scrollY + cursorRelY;
            if (cursorAbsY < innerY) {
                scrollY += innerY - cursorAbsY;
            } else if (cursorAbsY + lineHeight > innerY + innerH) {
                scrollY -= cursorAbsY + lineHeight - (innerY + innerH);
            }

            float totalH = getLineCount() * lineHeight;
            float maxScroll = Math.max(0, totalH - innerH);
            scrollY = Math.max(0, Math.min(maxScroll, scrollY));
        }

        scrollX = Math.max(0, scrollX);
    }

    private float getLineHeight() {
        return textSize * LINE_HEIGHT_MULT;
    }

    private int getLineCount() {
        if (text.length() == 0) return 1;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private String getLineText(int line) {
        int start = getLineStart(line);
        int end = getLineEnd(line);
        if (start >= end) return "";
        return text.substring(start, end);
    }

    private int getLineStart(int line) {
        int currentLine = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (currentLine == line) return i;
            if (i < text.length() && text.charAt(i) == '\n') currentLine++;
        }
        return text.length();
    }

    private int getLineEnd(int line) {
        int currentLine = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (currentLine == line) return i;
                currentLine++;
            }
        }
        return currentLine == line ? text.length() : text.length();
    }

    private int getCursorLine() {
        int line = 0;
        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
