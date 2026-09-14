package polaris.api.module.impl.combat.aura.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class RotationPresetManager {
   public static final String[] MULTIPOINT = new String[]{"Multipoint", "Center", "Eyes", "Closest"};
   public static final String[] CYCLE = new String[]{"Cycle", "Closest", "Random"};
   public static final String[] SMOOTH = new String[]{"Smooth", "Static", "Locked"};
   public static final String[] FUNTIME = new String[]{"FunTime", "Spooky", "Holy", "Matrix", "Smooth", "Snap"};
   public static final String CUSTOM = "Custom";
   public static final int INT_VALUE = 12;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static RotationPresetManager instance;
   @SerializedName("name")
   public String funtime = "FunTime";
   @SerializedName("engine")
   public String funtime2 = "FunTime";
   @SerializedName("preset")
   public String funtime3 = "FunTime";
   @SerializedName("pointMode")
   public String multipoint = "Multipoint";
   @SerializedName("yawSpeedMin")
   public float floatValue = 35.0F;
   @SerializedName("yawSpeedMax")
   public float floatValue2 = 55.0F;
   @SerializedName("pitchSpeedMin")
   public float floatValue3 = 6.0F;
   @SerializedName("pitchSpeedMax")
   public float floatValue4 = 12.0F;
   @SerializedName("attackYawSpeed")
   public float floatValue5 = 65.0F;
   @SerializedName("attackPitchSpeed")
   public float floatValue6 = 22.0F;
   @SerializedName("yawRandom")
   public float floatValue7 = 4.0F;
   @SerializedName("pitchRandom")
   public float floatValue8 = 3.0F;
   @SerializedName("oscillateX")
   public float floatValue9 = 0.2F;
   @SerializedName("oscillateY")
   public float floatValue10 = 0.12F;
   @SerializedName("oscillateSpeed")
   public float floatValue11 = 1.0F;
   @SerializedName("sidePointOffset")
   public float floatValue12 = 0.0F;
   @SerializedName("returnSpeed")
   public float floatValue13 = 30.0F;
   @SerializedName("moveHead")
   public boolean flag = false;
   @SerializedName("multipointMode")
   public String cycle = "Cycle";
   @SerializedName("pointDwell")
   public float floatValue14 = 0.9F;
   @SerializedName("pitchFollow")
   public String smooth = "Smooth";
   @SerializedName("yawOffset")
   public float floatValue15 = 0.0F;
   @SerializedName("pitchOffset")
   public float floatValue16 = 0.0F;
   @SerializedName("pitchMin")
   public float floatValue17 = -90.0F;
   @SerializedName("pitchMax")
   public float floatValue18 = 90.0F;
   @SerializedName("headLead")
   public float floatValue19 = 0.0F;
   @SerializedName("overlayLerp")
   public float floatValue20 = 0.35F;
   @SerializedName("overlayAimSpeed")
   public float floatValue21 = 1.0F;
   @SerializedName("pointSwitchSpeed")
   public float floatValue22 = 1.0F;
   @SerializedName("lookAway")
   public boolean flag2 = false;
   @SerializedName("lookAwayAngle")
   public float floatValue23 = 80.0F;
   @SerializedName("lookAwayInterval")
   public float floatValue24 = 5.0F;
   @SerializedName("points")
   public List<RotationPresetManager.RotationPresetManagerState> items = new ArrayList<>();
   public static final String WILD_ROT = "WILD-ROT:";

   private RotationPresetManager() {
   }

   public static synchronized RotationPresetManager resolve() {
      if (instance == null) {
         instance = resolve5();
      }

      instance.invoke();
      return instance;
   }

   public synchronized void invoke() {
      this.invoke6();
      if (this.multipoint == null || !check(MULTIPOINT, this.multipoint)) {
         this.multipoint = "Multipoint";
      }

      if (this.cycle == null || !check(CYCLE, this.cycle)) {
         this.cycle = "Cycle";
      }

      if (this.smooth == null || !check(SMOOTH, this.smooth)) {
         this.smooth = "Smooth";
      }

      if (this.items == null) {
         this.items = new ArrayList<>();
      }

      while (this.items.size() > 12) {
         this.items.remove(this.items.size() - 1);
      }

      for (RotationPresetManager.RotationPresetManagerState rotationPresetManagerState : this.items) {
         rotationPresetManagerState.floatValue = measure(rotationPresetManagerState.floatValue, -0.5F, 0.5F);
         rotationPresetManagerState.floatValue2 = measure(rotationPresetManagerState.floatValue2, 0.0F, 1.0F);
      }

      this.floatValue = measure(this.floatValue, 0.0F, 200.0F);
      this.floatValue2 = measure(this.floatValue2, 0.0F, 200.0F);
      if (this.floatValue2 < this.floatValue) {
         this.floatValue2 = this.floatValue;
      }

      this.floatValue3 = measure(this.floatValue3, 0.0F, 200.0F);
      this.floatValue4 = measure(this.floatValue4, 0.0F, 200.0F);
      if (this.floatValue4 < this.floatValue3) {
         this.floatValue4 = this.floatValue3;
      }

      this.floatValue5 = measure(this.floatValue5, 0.0F, 240.0F);
      this.floatValue6 = measure(this.floatValue6, 0.0F, 240.0F);
      this.floatValue7 = measure(this.floatValue7, 0.0F, 20.0F);
      this.floatValue8 = measure(this.floatValue8, 0.0F, 20.0F);
      this.floatValue9 = measure(this.floatValue9, 0.0F, 1.0F);
      this.floatValue10 = measure(this.floatValue10, 0.0F, 1.0F);
      this.floatValue11 = measure(this.floatValue11, 0.2F, 3.0F);
      this.floatValue12 = measure(this.floatValue12, 0.0F, 0.6F);
      this.floatValue13 = measure(this.floatValue13, 5.0F, 120.0F);
      this.floatValue14 = measure(this.floatValue14, 0.1F, 3.0F);
      this.floatValue15 = measure(this.floatValue15, -30.0F, 30.0F);
      this.floatValue16 = measure(this.floatValue16, -30.0F, 30.0F);
      this.floatValue17 = measure(this.floatValue17, -90.0F, 0.0F);
      this.floatValue18 = measure(this.floatValue18, 0.0F, 90.0F);
      this.floatValue19 = measure(this.floatValue19, 0.0F, 0.6F);
      this.floatValue20 = measure(this.floatValue20, 0.05F, 1.0F);
      this.floatValue21 = measure(this.floatValue21, 0.2F, 3.0F);
      this.floatValue22 = measure(this.floatValue22, 0.1F, 3.0F);
      this.floatValue23 = measure(this.floatValue23, 0.0F, 90.0F);
      this.floatValue24 = measure(this.floatValue24, 1.5F, 15.0F);
   }

   public synchronized void invoke2(float f, float g) {
      if (this.items.size() < 12) {
         this.items.add(new RotationPresetManager.RotationPresetManagerState(measure(f, -0.5F, 0.5F), measure(g, 0.0F, 1.0F)));
         invoke10();
      }
   }

   public synchronized void invoke3() {
      this.items.clear();
      invoke10();
   }

   public synchronized void invoke4(RotationPresetManager.RotationPresetManagerState rotationPresetManagerState2) {
      this.items.remove(rotationPresetManagerState2);
      invoke10();
   }

   private static boolean check(String[] strings, String string) {
      for (String text : strings) {
         if (text.equals(string)) {
            return true;
         }
      }

      return false;
   }

   public synchronized void invoke5(String string) {
      switch (string) {
         case "FunTime":
            this.multipoint = "Multipoint";
            this.floatValue = 35.0F;
            this.floatValue2 = 55.0F;
            this.floatValue3 = 5.0F;
            this.floatValue4 = 10.0F;
            this.floatValue5 = 65.0F;
            this.floatValue6 = 22.0F;
            this.floatValue7 = 4.3F;
            this.floatValue8 = 3.6F;
            this.floatValue9 = 0.2F;
            this.floatValue10 = 0.13F;
            this.floatValue11 = 1.0F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 30.0F;
            break;
         case "Spooky":
            this.multipoint = "Multipoint";
            this.floatValue = 40.0F;
            this.floatValue2 = 60.0F;
            this.floatValue3 = 10.0F;
            this.floatValue4 = 21.0F;
            this.floatValue5 = 60.0F;
            this.floatValue6 = 25.0F;
            this.floatValue7 = 3.0F;
            this.floatValue8 = 6.0F;
            this.floatValue9 = 0.12F;
            this.floatValue10 = 0.05F;
            this.floatValue11 = 1.2F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 30.0F;
            break;
         case "Holy":
            this.multipoint = "Center";
            this.floatValue = 50.0F;
            this.floatValue2 = 70.0F;
            this.floatValue3 = 10.0F;
            this.floatValue4 = 20.0F;
            this.floatValue5 = 70.0F;
            this.floatValue6 = 24.0F;
            this.floatValue7 = 2.0F;
            this.floatValue8 = 2.0F;
            this.floatValue9 = 0.2F;
            this.floatValue10 = 0.3F;
            this.floatValue11 = 0.7F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 30.0F;
            break;
         case "Matrix":
            this.multipoint = "Eyes";
            this.floatValue = 38.0F;
            this.floatValue2 = 43.0F;
            this.floatValue3 = 3.0F;
            this.floatValue4 = 5.0F;
            this.floatValue5 = 43.0F;
            this.floatValue6 = 6.0F;
            this.floatValue7 = 3.0F;
            this.floatValue8 = 4.0F;
            this.floatValue9 = 0.4F;
            this.floatValue10 = 0.02F;
            this.floatValue11 = 1.4F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 30.0F;
            break;
         case "Smooth":
            this.multipoint = "Center";
            this.floatValue = 18.0F;
            this.floatValue2 = 26.0F;
            this.floatValue3 = 4.0F;
            this.floatValue4 = 8.0F;
            this.floatValue5 = 30.0F;
            this.floatValue6 = 12.0F;
            this.floatValue7 = 0.5F;
            this.floatValue8 = 0.5F;
            this.floatValue9 = 0.0F;
            this.floatValue10 = 0.0F;
            this.floatValue11 = 1.0F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 20.0F;
            break;
         case "Snap":
            this.multipoint = "Closest";
            this.floatValue = 120.0F;
            this.floatValue2 = 180.0F;
            this.floatValue3 = 80.0F;
            this.floatValue4 = 120.0F;
            this.floatValue5 = 200.0F;
            this.floatValue6 = 160.0F;
            this.floatValue7 = 1.0F;
            this.floatValue8 = 1.0F;
            this.floatValue9 = 0.0F;
            this.floatValue10 = 0.0F;
            this.floatValue11 = 1.0F;
            this.floatValue12 = 0.0F;
            this.floatValue13 = 40.0F;
         case "Custom":
      }

      this.funtime = string;
      if ("Custom".equals(string)) {
         this.funtime2 = "Custom";
         this.funtime3 = "Custom";
      } else if (check(FUNTIME, string)) {
         this.funtime2 = string;
         this.funtime3 = string;
      } else {
         this.funtime2 = "Custom";
         this.funtime3 = "Custom";
      }

      this.invoke();
      invoke10();
   }

   private synchronized void invoke6() {
      String text2 = resolve3(this.funtime3);
      if (text2 == null) {
         text2 = resolve3(this.funtime);
      }

      if (text2 == null) {
         text2 = resolve3(this.funtime2);
      }

      if (text2 != null) {
         this.funtime3 = text2;
         this.funtime = text2;
         this.funtime2 = text2;
      } else if (this.check2()) {
         this.funtime = "Custom";
         this.funtime2 = "Custom";
         this.funtime3 = "Custom";
      } else {
         if (this.funtime2 == null || this.funtime2.isBlank()) {
            this.funtime2 = "Custom";
         }

         if (!"Custom".equals(this.funtime2) && !check(FUNTIME, this.funtime2)) {
            this.funtime2 = "Custom";
         }

         if (this.funtime == null || this.funtime.isBlank()) {
            this.funtime = this.funtime2;
         }

         if (this.funtime3 == null || this.funtime3.isBlank()) {
            this.funtime3 = "Custom".equals(this.funtime2) ? "Custom" : this.funtime2;
         }
      }
   }

   private boolean check2() {
      return "Custom".equalsIgnoreCase(resolve2(this.funtime3))
         || "Custom".equalsIgnoreCase(resolve2(this.funtime))
         || "custom".equalsIgnoreCase(resolve2(this.funtime));
   }

   private static String resolve2(String string) {
      return string == null ? "" : string.trim();
   }

   private static String resolve3(String string) {
      if (string != null && !string.isBlank()) {
         String text3 = string.trim();
         if (!"Custom".equalsIgnoreCase(text3) && !"custom".equalsIgnoreCase(text3)) {
            for (String text4 : FUNTIME) {
               if (text4.equalsIgnoreCase(text3)) {
                  return text4;
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public synchronized void invoke7() {
      this.invoke5("FunTime");
      this.items.clear();
      this.cycle = "Cycle";
      this.floatValue14 = 0.9F;
      this.floatValue22 = 1.0F;
      this.flag = false;
      this.invoke8();
      this.invoke();
      invoke10();
   }

   private synchronized void invoke8() {
      this.smooth = "Smooth";
      this.floatValue15 = 0.0F;
      this.floatValue16 = 0.0F;
      this.floatValue17 = -90.0F;
      this.floatValue18 = 90.0F;
      this.floatValue19 = 0.0F;
      this.floatValue20 = 0.35F;
      this.floatValue21 = 1.0F;
      this.flag2 = false;
      this.floatValue23 = 80.0F;
      this.floatValue24 = 5.0F;
   }

   public synchronized String resolve4() {
      this.invoke();
      String text5 = GSON.toJson(this);
      String text6 = Base64.getUrlEncoder().withoutPadding().encodeToString(text5.getBytes(StandardCharsets.UTF_8));
      return "POLARIS-ROT:" + text6;
   }

   public static synchronized boolean check3(String string) {
      if (string == null) {
         return false;
      } else {
         String text7 = string.trim();
         if (text7.startsWith("POLARIS-ROT:")) {
            text7 = text7.substring("POLARIS-ROT:".length());
         } else if (text7.startsWith("WILD-ROT:")) {
            text7 = text7.substring("WILD-ROT:".length());
         }

         text7 = text7.trim();
         if (text7.isEmpty()) {
            return false;
         } else {
            try {
               byte[] byteValues = Base64.getUrlDecoder().decode(text7);
               String text8 = new String(byteValues, StandardCharsets.UTF_8);
               RotationPresetManager rotationPresetManager = (RotationPresetManager)GSON.fromJson(text8, RotationPresetManager.class);
               if (rotationPresetManager == null) {
                  return false;
               } else {
                  RotationPresetManager rotationPresetManager2 = resolve();
                  rotationPresetManager2.invoke9(rotationPresetManager);
                  rotationPresetManager2.invoke();
                  invoke10();
                  return true;
               }
            } catch (Throwable exception) {
               return false;
            }
         }
      }
   }

   private synchronized void invoke9(RotationPresetManager rotationPresetManager3) {
      this.funtime = rotationPresetManager3.funtime;
      this.funtime2 = rotationPresetManager3.funtime2;
      this.funtime3 = rotationPresetManager3.funtime3;
      this.multipoint = rotationPresetManager3.multipoint;
      this.floatValue = rotationPresetManager3.floatValue;
      this.floatValue2 = rotationPresetManager3.floatValue2;
      this.floatValue3 = rotationPresetManager3.floatValue3;
      this.floatValue4 = rotationPresetManager3.floatValue4;
      this.floatValue5 = rotationPresetManager3.floatValue5;
      this.floatValue6 = rotationPresetManager3.floatValue6;
      this.floatValue7 = rotationPresetManager3.floatValue7;
      this.floatValue8 = rotationPresetManager3.floatValue8;
      this.floatValue9 = rotationPresetManager3.floatValue9;
      this.floatValue10 = rotationPresetManager3.floatValue10;
      this.floatValue11 = rotationPresetManager3.floatValue11;
      this.floatValue12 = rotationPresetManager3.floatValue12;
      this.floatValue13 = rotationPresetManager3.floatValue13;
      this.flag = rotationPresetManager3.flag;
      this.cycle = rotationPresetManager3.cycle;
      this.floatValue14 = rotationPresetManager3.floatValue14;
      this.smooth = rotationPresetManager3.smooth;
      this.floatValue15 = rotationPresetManager3.floatValue15;
      this.floatValue16 = rotationPresetManager3.floatValue16;
      this.floatValue17 = rotationPresetManager3.floatValue17;
      this.floatValue18 = rotationPresetManager3.floatValue18;
      this.floatValue19 = rotationPresetManager3.floatValue19;
      this.floatValue20 = rotationPresetManager3.floatValue20;
      this.floatValue21 = rotationPresetManager3.floatValue21;
      this.floatValue22 = rotationPresetManager3.floatValue22;
      this.flag2 = rotationPresetManager3.flag2;
      this.floatValue23 = rotationPresetManager3.floatValue23;
      this.floatValue24 = rotationPresetManager3.floatValue24;
      this.items = new ArrayList<>();
      if (rotationPresetManager3.items != null) {
         for (RotationPresetManager.RotationPresetManagerState rotationPresetManagerState3 : rotationPresetManager3.items) {
            if (rotationPresetManagerState3 != null) {
               this.items.add(new RotationPresetManager.RotationPresetManagerState(rotationPresetManagerState3.floatValue, rotationPresetManagerState3.floatValue2));
            }
         }
      }
   }

   public static synchronized void invoke10() {
      if (instance != null) {
         File file = resolve6();
         if (file != null) {
            try {
               File file2 = file.getParentFile();
               if (file2 != null && !file2.exists()) {
                  file2.mkdirs();
               }

               try (FileWriter fileWriter = new FileWriter(file)) {
                  GSON.toJson(instance, fileWriter);
               }
            } catch (Throwable exception2) {
            }
         }
      }
   }

   private static RotationPresetManager resolve5() {
      File file3 = resolve6();
      if (file3 != null && file3.exists()) {
         try {
            RotationPresetManager rotationPresetManager4;
            try (FileReader fileReader = new FileReader(file3)) {
               RotationPresetManager rotationPresetManager5 = (RotationPresetManager)GSON.fromJson(fileReader, RotationPresetManager.class);
               if (rotationPresetManager5 == null) {
                  return new RotationPresetManager();
               }

               rotationPresetManager4 = rotationPresetManager5;
            }

            return rotationPresetManager4;
         } catch (Throwable exception3) {
            return new RotationPresetManager();
         }
      } else {
         RotationPresetManager rotationPresetManager6 = new RotationPresetManager();
         rotationPresetManager6.invoke();
         return rotationPresetManager6;
      }
   }

   private static File resolve6() {
      File dir = null;
      try {
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (mc != null && mc.gameDirectory != null) {
            dir = new File(mc.gameDirectory, "polaris/AI");
         }
      } catch (Throwable ignored) {
      }
      if (dir == null) {
         return null;
      }
      if (!dir.exists()) {
         dir.mkdirs();
      }
      return new File(dir, "custom-rotation.json");
   }

   private static float measure(float f, float g, float h) {
      return !Float.isFinite(f) ? g : Math.max(g, Math.min(h, f));
   }

   public static final class RotationPresetManagerState {
      @SerializedName("x")
      public float floatValue;
      @SerializedName("y")
      public float floatValue2;

      public RotationPresetManagerState() {
      }

      public RotationPresetManagerState(float f, float g) {
         this.floatValue = f;
         this.floatValue2 = g;
      }
   }
}
