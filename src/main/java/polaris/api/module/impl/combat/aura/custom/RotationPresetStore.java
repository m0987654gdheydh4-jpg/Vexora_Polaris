package polaris.api.module.impl.combat.aura.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class RotationPresetStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final RotationPresetStore INSTANCE = new RotationPresetStore();
   private final List<RotationPresetStore.RotationPresetStoreTimedEntry> items = new ArrayList<>();
   private boolean flag;

   private RotationPresetStore() {
   }

   public static RotationPresetStore getINSTANCE() {
      return INSTANCE;
   }

   public synchronized List<RotationPresetStore.RotationPresetStoreTimedEntry> resolve() {
      this.invoke();
      return List.copyOf(this.items);
   }

   public synchronized RotationPresetStore.RotationPresetStoreTimedEntry resolve2(String string, RotationPresetManager rotationPresetManager) {
      this.invoke();
      String text = resolve6(string);
      if (!text.isEmpty() && rotationPresetManager != null) {
         long longValue = System.currentTimeMillis();
         RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry = new RotationPresetStore.RotationPresetStoreTimedEntry(UUID.randomUUID().toString(), text, rotationPresetManager.resolve4(), longValue, longValue);
         this.items.add(0, rotationPresetStoreTimedEntry);
         this.invoke2();
         return rotationPresetStoreTimedEntry;
      } else {
         return null;
      }
   }

   public synchronized RotationPresetStore.RotationPresetStoreTimedEntry resolve3(String string, String string2, RotationPresetManager rotationPresetManager2) {
      this.invoke();
      String text2 = resolve6(string2);
      if (string != null && !text2.isEmpty() && rotationPresetManager2 != null) {
         for (int intValue = 0; intValue < this.items.size(); intValue++) {
            RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry2 = this.items.get(intValue);
            if (rotationPresetStoreTimedEntry2.id().equals(string)) {
               RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry3 = new RotationPresetStore.RotationPresetStoreTimedEntry(rotationPresetStoreTimedEntry2.id(), text2, rotationPresetManager2.resolve4(), rotationPresetStoreTimedEntry2.createdAt(), System.currentTimeMillis());
               this.items.set(intValue, rotationPresetStoreTimedEntry3);
               this.invoke3();
               this.invoke2();
               return rotationPresetStoreTimedEntry3;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public synchronized boolean check(String string) {
      RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry4 = this.resolve4(string);
      return rotationPresetStoreTimedEntry4 != null && RotationPresetManager.check3(rotationPresetStoreTimedEntry4.key());
   }

   public synchronized boolean check2(String string) {
      this.invoke();
      boolean flag = this.items.removeIf(rotationPresetStoreTimedEntry5 -> rotationPresetStoreTimedEntry5.id().equals(string));
      if (flag) {
         this.invoke2();
      }

      return flag;
   }

   public synchronized RotationPresetStore.RotationPresetStoreTimedEntry resolve4(String string) {
      this.invoke();
      if (string == null) {
         return null;
      } else {
         for (RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry6 : this.items) {
            if (rotationPresetStoreTimedEntry6.id().equals(string)) {
               return rotationPresetStoreTimedEntry6;
            }
         }

         return null;
      }
   }

   private void invoke() {
      if (!this.flag) {
         File file = resolve7();
         if (file != null) {
            this.flag = true;
            if (file.isFile()) {
               try {
                  String text3 = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                  RotationPresetStore.RotationPresetStoreData rotationPresetStoreData = (RotationPresetStore.RotationPresetStoreData)GSON.fromJson(text3, RotationPresetStore.RotationPresetStoreData.class);
                  if (rotationPresetStoreData == null || rotationPresetStoreData.presets == null) {
                     return;
                  }

                  for (RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry7 : rotationPresetStoreData.presets) {
                     RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry8 = resolve5(rotationPresetStoreTimedEntry7);
                     if (rotationPresetStoreTimedEntry8 != null && this.items.stream().noneMatch(rotationPresetStoreTimedEntry9 -> rotationPresetStoreTimedEntry9.id().equals(rotationPresetStoreTimedEntry8.id()))) {
                        this.items.add(rotationPresetStoreTimedEntry8);
                     }
                  }

                  this.invoke3();
               } catch (Throwable exception) {
               }
            }
         }
      }
   }

   private void invoke2() {
      File file2 = resolve7();
      if (file2 != null) {
         try {
            File file3 = file2.getParentFile();
            if (file3 != null) {
               Files.createDirectories(file3.toPath());
            }

            Files.writeString(file2.toPath(), GSON.toJson(new RotationPresetStore.RotationPresetStoreData(1, this.items)), StandardCharsets.UTF_8);
         } catch (Throwable exception2) {
         }
      }
   }

   private void invoke3() {
      this.items.sort(Comparator.comparingLong(RotationPresetStore.RotationPresetStoreTimedEntry::updatedAt).reversed());
   }

   private static RotationPresetStore.RotationPresetStoreTimedEntry resolve5(RotationPresetStore.RotationPresetStoreTimedEntry rotationPresetStoreTimedEntry10) {
      if (rotationPresetStoreTimedEntry10 != null && rotationPresetStoreTimedEntry10.key() != null && !rotationPresetStoreTimedEntry10.key().isBlank()) {
         String text4 = resolve6(rotationPresetStoreTimedEntry10.name());
         if (text4.isEmpty()) {
            return null;
         } else {
            String text5 = rotationPresetStoreTimedEntry10.id() != null && !rotationPresetStoreTimedEntry10.id().isBlank() ? rotationPresetStoreTimedEntry10.id() : UUID.randomUUID().toString();
            long longValue2 = Math.max(0L, rotationPresetStoreTimedEntry10.createdAt());
            long longValue3 = Math.max(longValue2, rotationPresetStoreTimedEntry10.updatedAt());
            return new RotationPresetStore.RotationPresetStoreTimedEntry(text5, text4, rotationPresetStoreTimedEntry10.key().trim(), longValue2, longValue3);
         }
      } else {
         return null;
      }
   }

   private static String resolve6(String string) {
      if (string == null) {
         return "";
      } else {
         String text6 = string.replaceAll("\\p{Cntrl}", "").trim().replaceAll("\\s{2,}", " ");
         return text6.length() > 40 ? text6.substring(0, 40).trim() : text6;
      }
   }

   private static File resolve7() {
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
      return new File(dir, "custom-rotation-presets.json");
   }

   public record RotationPresetStoreTimedEntry(String id, String name, String key, long createdAt, long updatedAt) {
   }

   record RotationPresetStoreData(int version, List<RotationPresetStore.RotationPresetStoreTimedEntry> presets) {
   }
}
