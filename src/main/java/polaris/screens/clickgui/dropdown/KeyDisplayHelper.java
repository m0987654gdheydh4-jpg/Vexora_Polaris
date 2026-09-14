package polaris.screens.clickgui.dropdown;

import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.bind.InputType;
import org.lwjgl.glfw.GLFW;

public final class KeyDisplayHelper {
    private KeyDisplayHelper() {
    }

    public static String getShortKeyName(KeyBind bind) {
        if (bind == null || !bind.isBound()) {
            return "N";
        }
        return getShortKeyName(bind.getType(), bind.getCode());
    }

    public static String getShortKeyName(InputType type, int code) {
        if (type == InputType.NONE || code < 0) {
            return "N";
        }
        if (type == InputType.MOUSE) {
            return switch (code) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                default -> "M" + (code + 1);
            };
        }
        return switch (code) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "ALT";
            case GLFW.GLFW_KEY_SPACE -> "SPC";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "ENT";
            case GLFW.GLFW_KEY_BACKSPACE -> "BSPC";
            case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> shortKeyboardName(code);
        };
    }

    private static String shortKeyboardName(int key) {
        String name = GLFW.glfwGetKeyName(key, -1);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase();
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        return String.valueOf(key);
    }
}

