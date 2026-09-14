package polaris.api.module.impl.combat.aura.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.utils.modules.warden.rotation.Rotation;
import polaris.utils.string.chat.ChatMessage;

public final class AiRotationTrainer {
   private static final int INT_VALUE = 3;
   private static final double DOUBLE_VALUE = 8.0;
   private static final double DOUBLE_VALUE_2 = 0.14;
   private static final double DOUBLE_VALUE_3 = 0.08;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final AtomicReference<AiRotationStatus> ATOMIC_REFERENCE = new AtomicReference<>(AiRotationStatus.idle());
   private static final CopyOnWriteArrayList<Consumer<AiRotationStatus>> COPY_ON_WRITE_ARRAY_LIST = new CopyOnWriteArrayList<>();
   
   private static volatile AiRotationTrainer.AiRotationTrainerState aiRotationTrainerState = new AiRotationTrainer.AiRotationTrainerState();
   private static LivingEntity livingEntity;
   private static boolean flag;
   private static boolean flag2;
   private static boolean flag3;
   private static boolean flag4;
   private static int intValue;
   private static int intValue2 = -1;
   private static int intValue3 = -1;
   private static long timestamp;
   private static long timestamp2 = -1L;
   private static int intValue4;
   private static int intValue5 = Integer.MIN_VALUE;
   private static long timestamp3;
   private static long timestamp4;
   private static int intValue6;
   private static int intValue7;
   private static float floatValue;
   private static float floatValue2;
   private static float floatValue3;
   private static float floatValue4;
   private static double doubleValue;
   private static AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState2;
   private static final float FLOAT_VALUE = 0.85F;
   private static final float FLOAT_VALUE_2 = 0.3F;
   private static final float FLOAT_VALUE_3 = 0.09F;
   private static final float FLOAT_VALUE_4 = 0.05F;
   private static final float FLOAT_VALUE_5 = 0.35F;
   private static final float FLOAT_VALUE_6 = 1.5F;
   private static final float FLOAT_VALUE_7 = 0.45F;
   private static final float FLOAT_VALUE_8 = 38.0F;
   private static final float FLOAT_VALUE_9 = 24.0F;
   private static final float FLOAT_VALUE_10 = 12.0F;
   private static final float FLOAT_VALUE_11 = 8.0F;
   private static final float FLOAT_VALUE_12 = 0.3F;
   private static float floatValue5;
   private static float floatValue6;
   private static float floatValue7;
   private static float floatValue8;
   private static String defaultValue = "default";
   private static final int INT_VALUE_2 = 16;
   private static final int INT_VALUE_3 = 2;
   private static final int INT_VALUE_4 = 48;
   private static final int INT_VALUE_5 = 32;
   private static final int INT_VALUE_6 = 2;
   private static final int INT_VALUE_7 = 3;
   private static final float FLOAT_VALUE_13 = 180.0F;
   private static final float FLOAT_VALUE_14 = 90.0F;
   private static final float FLOAT_VALUE_15 = 30.0F;
   private static final float FLOAT_VALUE_16 = 6.0F;
   private static final float FLOAT_VALUE_17 = 3.0F;
   private static final float FLOAT_VALUE_18 = 0.6F;
   private static final float FLOAT_VALUE_19 = 10.0F;
   private static final float FLOAT_VALUE_20 = 0.55F;
   private static final float FLOAT_VALUE_21 = 35.0F;
   private static final float FLOAT_VALUE_22 = 18.0F;
   private static final float FLOAT_VALUE_23 = 0.5F;
   private static final float[] FLOATS = new float[16];
   
   
   
   private static volatile AiRotationDataset aiRotationDataset;
   private static volatile boolean flag5;
   private static volatile boolean flag6;
   private static Thread thread;
   private static float floatValue9;
   private static float floatValue10;
   private static float floatValue11;
   private static float floatValue12;
   private static float floatValue13;
   private static float floatValue14;
   private static int intValue8;
   private static float floatValue15;
   
   private static boolean silentReady;
   private static float silentYaw;
   private static float silentPitch;
   private static float floatValue16;
   private static final int[] INTS = new int[3];
   private static final int INT_VALUE_8 = 160;
   private static final float[] FLOATS_2 = new float[160];
   private static final float[] FLOATS_3 = new float[160];
   private static int intValue9;
   private static volatile boolean flag7;
   private static volatile float[] floats;
   private static volatile float[] floats2;
   private static volatile float floatValue17 = -1.0F;
   private static volatile int intValue10;
   private static volatile float floatValue18;
   private static volatile float floatValue19;
   private static volatile float floatValue20;
   private static volatile long timestamp5;
   private static int intValue11;

   private AiRotationTrainer() {
   }
   private static Minecraft mc() {
      return Minecraft.getInstance();
   }

   private static Input resolvePlayerInput() {
      if (mc().player == null) {
         return new Input(false, false, false, false, false, false, false);
      }
      if (mc().player.input != null && mc().player.input.keyPresses != null) {
         return mc().player.input.keyPresses;
      }
      return new Input(
         mc().options.keyUp.isDown(),
         mc().options.keyDown.isDown(),
         mc().options.keyLeft.isDown(),
         mc().options.keyRight.isDown(),
         mc().options.keyJump.isDown(),
         mc().options.keyShift.isDown(),
         mc().options.keySprint.isDown()
      );
   }


   public static synchronized String resolve() {
      if (mc().player != null && mc().level != null) {
         flag = true;
         flag2 = false;
         aiRotationTrainerState = new AiRotationTrainer.AiRotationTrainerState();
         aiRotationTrainerState.timestamp = System.currentTimeMillis();
         aiRotationTrainerState.floatValue = measure3();
         livingEntity = null;
         flag3 = false;
         flag4 = false;
         intValue = 0;
         intValue2 = -1;
         intValue3 = -1;
         timestamp = 0L;
         timestamp2 = -1L;
         intValue6 = 0;
         floatValue = mc().player.getYRot();
         floatValue2 = mc().player.getXRot();
         floatValue3 = 0.0F;
         floatValue4 = 0.0F;
         doubleValue = mc().player.getDeltaMovement().y;
         invoke8("TRAIN start profile=" + defaultValue + " sens=" + String.format(Locale.ROOT, "%.3f", aiRotationTrainerState.floatValue), false);
         invoke16("AI recording: waiting target");
         return "Запись начата в профиль '" + defaultValue + "'. Ударьте игрока, моба или WildBot.";
      } else {
         return "Игрок не готов.";
      }
   }

   public static synchronized String resolve2() {
      if (flag) {
         flag = false;
         livingEntity = null;
         if (aiRotationTrainerState.items.isEmpty()) {
            invoke16("AI recording empty");
            return "Запись остановлена: паттерн пуст.";
         } else if (!check6()) {
            invoke16("AI save failed");
            return "Не удалось сохранить паттерн.";
         } else {
            intValue7 = aiRotationTrainerState.items.size();
            invoke16("AI ready: " + intValue7 + " frames");
            return "Профиль '" + defaultValue + "' сохранён: " + intValue7 + " тиков, ударов: " + intValue6 + ".";
         }
      } else if (flag2) {
         flag2 = false;
         flag5 = false;
         flag4 = false;
         aiRotationTrainerState2 = null;
         clearSilent();
         invoke11();
         invoke16("AI stopped");
         return "Воспроизведение остановлено.";
      } else {
         return "AI уже остановлен.";
      }
   }

   public static synchronized String resolve3() {
      if (flag) {
         return "Сначала завершите запись командой .ai stop.";
      } else {
         AiRotationDataset loadedDataset = AiRotationDataset.resolve(resolve22());
         boolean staleModel = loadedDataset != null && !loadedDataset.check(16, 2);
         if (staleModel) {
            loadedDataset = null;
         }

         AiRotationTrainer.AiRotationTrainerState patternState = resolve29();
         boolean hasPattern = patternState != null && patternState.items != null && !patternState.items.isEmpty();
         if (loadedDataset == null && !hasPattern) {
            invoke16("AI pattern missing");
            return staleModel ? "Модель устарела (новый формат). Переобучите: .ai learn." : "Нет модели и паттерна. Сначала .ai train, затем .ai learn.";
         } else {
            if (hasPattern) {
               invoke15(patternState);
               AiRotationTrainer.aiRotationTrainerState = patternState;
            } else {
               patternState = new AiRotationTrainer.AiRotationTrainerState();
               AiRotationTrainer.aiRotationTrainerState = patternState;
            }

            intValue7 = patternState.items.size();
            intValue6 = compute5(patternState.items);
            AiRotationTrainer.aiRotationDataset = loadedDataset;
            flag5 = loadedDataset != null;
            invoke11();
            
            AiRotationTrainer.flag2 = true;
            intValue4 = 0;
            intValue5 = Integer.MIN_VALUE;
            flag4 = false;
            timestamp3 = 0L;
            timestamp4 = 0L;
            aiRotationTrainerState2 = null;
            if (flag5) {
               invoke8("RUN model profile=" + defaultValue, false);
               invoke16("AI brain ready");
               return "Нейромодель профиля '" + defaultValue + "' запущена.";
            } else {
               invoke8("RUN replay profile=" + defaultValue + " frames=" + intValue7, false);
               invoke16("AI ready: " + intValue7 + " frames");
               return "Воспроизведение профиля '" + defaultValue + "' запущено: " + intValue7 + " тиков (модель не обучена, .ai learn).";
            }
         }
      }
   }

   public static synchronized String resolve4() {
      if (flag) {
         return "Сначала завершите запись командой .ai stop.";
      } else if (flag6) {
         return "Обучение уже идёт. Дождитесь завершения.";
      } else {
         AiRotationTrainer.AiRotationTrainerState aiRotationTrainerState2 = resolve29();
         if (aiRotationTrainerState2 != null && aiRotationTrainerState2.items != null && aiRotationTrainerState2.items.size() >= 16) {
            invoke15(aiRotationTrainerState2);
            List items = aiRotationTrainerState2.items;
            int intValue = items.size();
            float[] floatValues = new float[intValue];
            float[] floatValues2 = new float[intValue];

            for (int intValue2 = 0; intValue2 < intValue; intValue2++) {
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState22 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue2);
               floatValues[intValue2] = aiRotationTrainerState22 == null ? 0.0F : aiRotationTrainerState22.floatValue7;
               floatValues2[intValue2] = aiRotationTrainerState22 == null ? 0.0F : aiRotationTrainerState22.floatValue8;
            }

            float[] floatValues3 = resolve6(floatValues, 2);
            float[] floatValues4 = resolve6(floatValues2, 2);
            float[] floatValues5 = new float[intValue];
            int intValue3 = 0;

            for (int intValue4 = 0; intValue4 < intValue; intValue4++) {
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState23 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue4);
               if (aiRotationTrainerState23 != null) {
                  floatValues5[intValue3++] = (float)aiRotationTrainerState23.doubleValue9;
               }
            }

            float[] floatValues6 = Arrays.copyOf(floatValues5, intValue3);
            Arrays.sort(floatValues6);
            float floatValue = measure2(floatValues6, 0.34F);
            float floatValue2 = measure2(floatValues6, 0.67F);
            float floatValue3 = floatValue;
            float floatValue4 = floatValue2 <= floatValue ? floatValue + 0.5F : floatValue2;
            int intValue5 = 0;
            int intValue6 = 0;
            int intValue7 = 0;
            float floatValue5 = 0.0F;

            for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState24 : (List<AiRotationTrainer.AiRotationTrainerState2>)items) {
               if (aiRotationTrainerState24 != null && aiRotationTrainerState24.flag9) {
                  intValue5++;
                  if (aiRotationTrainerState24.flag10) {
                     intValue6++;
                  } else {
                     intValue7++;
                     floatValue5 += Math.abs(aiRotationTrainerState24.floatValue5) + Math.abs(aiRotationTrainerState24.floatValue6);
                  }
               }
            }

            float floatValue6 = intValue5 > 0 ? (float)(intValue5 - intValue6) / intValue5 : 0.0F;
            float floatValue7 = intValue7 > 0 ? floatValue5 / intValue7 : 0.0F;
            float floatValue8 = aiRotationTrainerState2.floatValue;
            ArrayList arrayList = new ArrayList();
            ArrayList arrayList2 = new ArrayList();

            for (int intValue8 = 0; intValue8 < 3; intValue8++) {
               arrayList.add(new ArrayList());
               arrayList2.add(new ArrayList());
            }

            for (int intValue9 = 0; intValue9 < intValue; intValue9++) {
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState25 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue9);
               if (aiRotationTrainerState25 != null) {
                  int intValue10 = compute(aiRotationTrainerState25.doubleValue9, floatValue3, floatValue4);
                  ((List)arrayList.get(intValue10)).add(floatValues[intValue9] - floatValues3[intValue9]);
                  ((List)arrayList2.get(intValue10)).add(floatValues2[intValue9] - floatValues4[intValue9]);
               }
            }

            ArrayList arrayList3 = new ArrayList();
            ArrayList arrayList4 = new ArrayList();

            for (int intValue11 = 0; intValue11 < intValue - 1; intValue11++) {
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState26 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue11);
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState27 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue11 + 1);
               if (aiRotationTrainerState26 != null && aiRotationTrainerState27 != null) {
                  float floatValue9 = Mth.wrapDegrees(aiRotationTrainerState26.floatValue3 - aiRotationTrainerState26.floatValue);
                  float floatValue10 = aiRotationTrainerState26.floatValue4 - aiRotationTrainerState26.floatValue2;
                  float floatValue11 = 0.0F;
                  float floatValue12 = 0.0F;
                  if (intValue11 >= 1) {
                     AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState28 = (AiRotationTrainer.AiRotationTrainerState2)items.get(intValue11 - 1);
                     if (aiRotationTrainerState28 != null) {
                        floatValue11 = aiRotationTrainerState28.floatValue7;
                        floatValue12 = aiRotationTrainerState28.floatValue8;
                     }
                  }

                  float[] floatValues7 = new float[16];
                  invoke9(
                     floatValues7,
                     floatValue9,
                     floatValue10,
                     aiRotationTrainerState26.floatValue7,
                     aiRotationTrainerState26.floatValue8,
                     floatValue11,
                     floatValue12,
                     aiRotationTrainerState26.doubleValue9,
                     aiRotationTrainerState26.doubleValue10,
                     aiRotationTrainerState26.doubleValue7,
                     aiRotationTrainerState26.doubleValue14,
                     aiRotationTrainerState26.doubleValue11,
                     aiRotationTrainerState26.doubleValue13,
                     aiRotationTrainerState26.flag8,
                     aiRotationTrainerState26.flag7,
                     aiRotationTrainerState26.floatValue13,
                     aiRotationTrainerState26.intValue3
                  );
                  float[] floatValues8 = new float[]{Mth.clamp(floatValues[intValue11 + 1] / 30.0F, -1.0F, 1.0F), Mth.clamp(floatValues2[intValue11 + 1] / 30.0F, -1.0F, 1.0F)};
                  arrayList3.add(floatValues7);
                  arrayList4.add(floatValues8);
               }
            }

            if (arrayList3.size() < 8) {
               return "Слишком мало пар для обучения.";
            } else {
               float[][] floatValuesValues = (float[][])arrayList3.toArray(new float[0][]);
               float[][] floatValuesValues2 = (float[][])arrayList4.toArray(new float[0][]);
               float[][] floatValuesValues3 = resolve8(arrayList);
               float[][] floatValuesValues4 = resolve8(arrayList2);
               floats = resolve9(floatValues, 160);
               floats2 = resolve9(floatValues2, 160);
               intValue10 = floatValuesValues.length;
               floatValue17 = -1.0F;
               int intValue12 = Mth.clamp(500000 / floatValuesValues.length, 300, 1500);
               String text = defaultValue;
               Path path2 = resolve22();
               invoke8(
                  String.format(
                     Locale.ROOT,
                     "LEARN pairs=%d frames=%d buckets=[%d,%d,%d] thr=[%.2f,%.2f] miss=%.0f%% sens=%.3f epochs=%d",
                     floatValuesValues.length,
                     intValue,
                     floatValuesValues3[0].length,
                     floatValuesValues3[1].length,
                     floatValuesValues3[2].length,
                     floatValue3,
                     floatValue4,
                     floatValue6 * 100.0F,
                     floatValue8,
                     intValue12
                  ),
                  true
               );
               flag6 = true;
               invoke16("AI training: " + floatValuesValues.length + " pairs");
               thread = new Thread(() -> {
                  AiRotationNeuralNetwork var12x = new AiRotationNeuralNetwork(16, 48, 32, 2);

                  try {
                     var12x.invoke2(floatValuesValues, floatValuesValues2, intValue12, 0.002F);
                     float var13x = var12x.measure(floatValuesValues, floatValuesValues2);
                     floatValue17 = var13x;
                     invoke8("LEARN done loss=" + String.format(Locale.ROOT, "%.5f", var13x), false);
                     AiRotationDataset var14x = new AiRotationDataset(16, 2, 3, var12x, floatValuesValues3, floatValuesValues4);
                     var14x.floatValue = floatValue3;
                     var14x.floatValue2 = floatValue4;
                     var14x.floatValue3 = floatValue8;
                     var14x.floatValue4 = floatValue6;
                     var14x.floatValue5 = floatValue7;
                     boolean var15x = var14x.check2(path2);
                     synchronized (AiRotationTrainer.class) {
                        if (var15x && text.equals(defaultValue)) {
                           aiRotationDataset = var14x;
                           
                           flag5 = true;
                        }
                     }

                     invoke16(var15x ? "AI brain ready (loss " + String.format(Locale.ROOT, "%.4f", var13x) + ")" : "AI train save failed");
                  } catch (Throwable var23x) {
                     invoke16("AI train failed");
                  } finally {
                     flag6 = false;
                  }
               }, "Wild-AI-Train");
               thread.setDaemon(true);
               thread.start();
               return "Обучение профиля '" + text + "' запущено в фоне: " + floatValuesValues.length + " пар, эпох: " + intValue12 + ".";
            }
         } else {
            return "Недостаточно данных (нужно >= 16 тиков). Сначала .ai train.";
         }
      }
   }

   public static synchronized void invoke(Entity target) {
      if (target instanceof LivingEntity livingEntity2 && livingEntity2 != mc().player) {
         if (flag) {
            if (livingEntity == null || livingEntity.getId() != livingEntity2.getId()) {
               livingEntity = livingEntity2;
               floatValue = mc().player.getYRot();
               floatValue2 = mc().player.getXRot();
               floatValue3 = 0.0F;
               floatValue4 = 0.0F;
               doubleValue = mc().player.getDeltaMovement().y;
            }

            long longValue = System.currentTimeMillis();
            flag3 = true;
            intValue3 = intValue2 < 0 ? -1 : Math.max(0, intValue - intValue2);
            timestamp2 = timestamp == 0L ? -1L : Math.max(0L, longValue - timestamp);
            intValue2 = intValue;
            timestamp = longValue;
            intValue6++;
            invoke16("AI recording: " + aiRotationTrainerState.items.size() + " frames");
         }

         if (flag2) {
            flag4 = false;
            timestamp3 = System.currentTimeMillis();
            timestamp4 = 0L;
            invoke8(String.format(Locale.ROOT, "ATTACK target=%d dist=%.2f", livingEntity2.getId(), mc().player.distanceTo(livingEntity2)), false);
         }
      }
   }

   public static synchronized void invoke2() {
      if (flag && mc().player != null && mc().level != null && livingEntity != null && !livingEntity.isRemoved()) {
         Vec3 vec3d2 = resolve14(livingEntity, mc().player.getYRot(), mc().player.getXRot());
         Rotation rotation = resolve16(vec3d2);
         if (rotation != null) {
            float floatValue13 = mc().player.getYRot();
            float floatValue14 = mc().player.getXRot();
            Vec3 vec3d3 = mc().player.getDeltaMovement();
            Input playerInput = resolvePlayerInput();
            AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState29 = new AiRotationTrainer.AiRotationTrainerState2();
            aiRotationTrainerState29.intValue = intValue;
            aiRotationTrainerState29.floatValue = floatValue13;
            aiRotationTrainerState29.floatValue2 = floatValue14;
            aiRotationTrainerState29.floatValue3 = rotation.floatValue;
            aiRotationTrainerState29.floatValue4 = rotation.floatValue2;
            aiRotationTrainerState29.floatValue5 = Mth.wrapDegrees(floatValue13 - rotation.floatValue);
            aiRotationTrainerState29.floatValue6 = floatValue14 - rotation.floatValue2;
            AABB box2 = livingEntity.getBoundingBox();
            aiRotationTrainerState29.doubleValue = measure6(vec3d2.x, box2.minX, box2.maxX, 0.14);
            aiRotationTrainerState29.doubleValue2 = measure6(vec3d2.y, box2.minY, box2.maxY, 0.08);
            aiRotationTrainerState29.doubleValue3 = measure6(vec3d2.z, box2.minZ, box2.maxZ, 0.14);
            aiRotationTrainerState29.flag = true;
            aiRotationTrainerState29.floatValue7 = Mth.wrapDegrees(floatValue13 - floatValue);
            aiRotationTrainerState29.floatValue8 = floatValue14 - floatValue2;
            aiRotationTrainerState29.floatValue9 = aiRotationTrainerState29.floatValue7 - floatValue3;
            aiRotationTrainerState29.floatValue10 = aiRotationTrainerState29.floatValue8 - floatValue4;
            aiRotationTrainerState29.flag2 = check4(floatValue3, aiRotationTrainerState29.floatValue7);
            aiRotationTrainerState29.flag3 = check4(floatValue4, aiRotationTrainerState29.floatValue8);
            aiRotationTrainerState29.flag4 = Math.abs(aiRotationTrainerState29.floatValue7) < 0.035F && Math.abs(aiRotationTrainerState29.floatValue8) < 0.035F;
            aiRotationTrainerState29.floatValue11 = (playerInput.forward() ? 1.0F : 0.0F) - (playerInput.backward() ? 1.0F : 0.0F);
            aiRotationTrainerState29.floatValue12 = (playerInput.left() ? 1.0F : 0.0F) - (playerInput.right() ? 1.0F : 0.0F);
            aiRotationTrainerState29.flag5 = playerInput.jump();
            aiRotationTrainerState29.flag6 = playerInput.shift();
            aiRotationTrainerState29.flag7 = playerInput.sprint() || mc().player.isSprinting();
            aiRotationTrainerState29.flag8 = mc().player.onGround();
            aiRotationTrainerState29.doubleValue4 = vec3d3.x;
            aiRotationTrainerState29.doubleValue5 = vec3d3.y;
            aiRotationTrainerState29.doubleValue6 = vec3d3.z;
            aiRotationTrainerState29.doubleValue7 = Math.hypot(vec3d3.x, vec3d3.z);
            aiRotationTrainerState29.doubleValue8 = vec3d3.y - doubleValue;
            aiRotationTrainerState29.doubleValue9 = mc().player.distanceTo(livingEntity);
            aiRotationTrainerState29.doubleValue10 = livingEntity.getY() - mc().player.getY();
            Vec3 vec3d4 = resolve18(livingEntity);
            aiRotationTrainerState29.doubleValue11 = vec3d4.x;
            aiRotationTrainerState29.doubleValue12 = vec3d4.y;
            aiRotationTrainerState29.doubleValue13 = vec3d4.z;
            aiRotationTrainerState29.doubleValue14 = Math.hypot(vec3d4.x, vec3d4.z);
            aiRotationTrainerState29.flag9 = flag3;
            aiRotationTrainerState29.flag10 = flag3 && EntityRaycastUtils.check4(floatValue13, floatValue14, mc().player.distanceTo(livingEntity) + 1.0, livingEntity, true);
            aiRotationTrainerState29.intValue2 = flag3 ? intValue3 : -1;
            aiRotationTrainerState29.timestamp = flag3 ? timestamp2 : -1L;
            aiRotationTrainerState29.floatValue13 = mc().player.getAttackStrengthScale(0.5F);
            aiRotationTrainerState29.flag11 = mc().player.swinging;
            aiRotationTrainerState29.floatValue14 = mc().player.attackAnim;
            aiRotationTrainerState29.intValue3 = livingEntity.hurtTime;
            aiRotationTrainerState.items.add(aiRotationTrainerState29);
            invoke7(aiRotationTrainerState29.floatValue7, aiRotationTrainerState29.floatValue8, false);
            if (aiRotationTrainerState29.flag9) {
               invoke8(
                  String.format(
                     Locale.ROOT,
                     "%s point=(%.2f,%.2f,%.2f) dist=%.2f yawOff=%.2f pitchOff=%.2f int=%dt/%dms",
                     aiRotationTrainerState29.flag10 ? "HIT" : "MISS",
                     aiRotationTrainerState29.doubleValue,
                     aiRotationTrainerState29.doubleValue2,
                     aiRotationTrainerState29.doubleValue3,
                     aiRotationTrainerState29.doubleValue9,
                     aiRotationTrainerState29.floatValue5,
                     aiRotationTrainerState29.floatValue6,
                     aiRotationTrainerState29.intValue2,
                     aiRotationTrainerState29.timestamp
                  ),
                  true
               );
            } else if ((aiRotationTrainerState29.intValue & 7) == 0) {
               invoke8(
                  String.format(
                     Locale.ROOT,
                     "REC t=%d aim=(%.2f,%.2f) yawD=%.2f pitchD=%.2f spd=%.3f dist=%.2f ground=%b sprint=%b",
                     aiRotationTrainerState29.intValue,
                     aiRotationTrainerState29.doubleValue,
                     aiRotationTrainerState29.doubleValue2,
                     aiRotationTrainerState29.floatValue7,
                     aiRotationTrainerState29.floatValue8,
                     aiRotationTrainerState29.doubleValue7,
                     aiRotationTrainerState29.doubleValue9,
                     aiRotationTrainerState29.flag8,
                     aiRotationTrainerState29.flag7
                  ),
                  true
               );
            }

            flag3 = false;
            intValue3 = -1;
            timestamp2 = -1L;
            floatValue = floatValue13;
            floatValue2 = floatValue14;
            floatValue3 = aiRotationTrainerState29.floatValue7;
            floatValue4 = aiRotationTrainerState29.floatValue8;
            doubleValue = vec3d3.y;
            intValue++;
            if ((intValue & 15) == 0) {
               invoke16("AI recording: " + aiRotationTrainerState.items.size() + " frames");
            }
         }
      }
   }

   public static synchronized void invoke3(LivingEntity livingEntity) {
      if (flag2 && !flag && mc().player != null && mc().level != null && livingEntity != null) {
         if (flag5 && aiRotationDataset != null) {
            invoke6(livingEntity);
         } else if (aiRotationTrainerState.items != null && !aiRotationTrainerState.items.isEmpty()) {
            if (intValue5 != livingEntity.getId()) {
               intValue5 = livingEntity.getId();
               intValue4 = ThreadLocalRandom.current().nextInt(aiRotationTrainerState.items.size());
               flag4 = false;
               timestamp3 = 0L;
               timestamp4 = 0L;
               invoke5();
            }

            AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState210 = aiRotationTrainerState.items.get(intValue4);
            aiRotationTrainerState2 = aiRotationTrainerState210;
            if (aiRotationTrainerState210.flag9 && !flag4) {
               flag4 = true;
               long longValue2 = compute4(aiRotationTrainerState210);
               timestamp4 = timestamp3 == 0L ? System.currentTimeMillis() : timestamp3 + longValue2;
            }

            Vec3 vec3d5 = resolve15(livingEntity, aiRotationTrainerState210);
            Rotation rotation2 = resolve16(vec3d5);
            if (rotation2 == null) {
               invoke4();
            } else {
               float[] aimBase = silentBase();
               float floatValue15 = aimBase[0];
               float floatValue16 = aimBase[1];
               
               boolean flag3 = EntityRaycastUtils.check4(
                       mc().player.getYRot(),
                       mc().player.getXRot(),
                       Math.max(8.0, mc().player.distanceTo(livingEntity) + 1.0),
                       livingEntity,
                       true
               );
               float floatValue17 = flag3 ? 0.85F : 0.3F;
               float floatValue18 = Math.abs(aiRotationTrainerState210.floatValue7) + Math.abs(aiRotationTrainerState210.floatValue8);
               float floatValue19 = 0.09F + floatValue18 * 0.05F;
               float floatValue20 = measure(floatValue19, true);
               float floatValue21 = measure(floatValue19, false);
               float floatValue22 = Mth.clamp(aiRotationTrainerState210.floatValue5 * floatValue17 + floatValue20, -12.0F, 12.0F);
               float floatValue23 = Mth.clamp(aiRotationTrainerState210.floatValue6 * floatValue17 + floatValue21, -8.0F, 8.0F);
               floatValue7 = floatValue7 + (floatValue22 - floatValue7) * 0.3F;
               floatValue8 = floatValue8 + (floatValue23 - floatValue8) * 0.3F;
               float floatValue24 = rotation2.floatValue + floatValue7;
               float floatValue25 = Mth.clamp(rotation2.floatValue2 + floatValue8, -90.0F, 90.0F);
               Rotation rotation3 = new Rotation(floatValue24, floatValue25);
               float floatValue26;
               float floatValue27;
               if (flag3) {
                  floatValue26 = Math.max(0.45F, measure8(aiRotationTrainerState210.floatValue7, aiRotationTrainerState210.flag4));
                  floatValue27 = Math.max(0.45F, measure8(aiRotationTrainerState210.floatValue8, aiRotationTrainerState210.flag4));
               } else {
                  float floatValue28 = Math.abs(Mth.wrapDegrees(floatValue24 - floatValue15));
                  float floatValue29 = Math.abs(floatValue25 - floatValue16);
                  floatValue26 = Math.min(floatValue28, 38.0F);
                  floatValue27 = Math.min(floatValue29, 24.0F);
               }

               pushSilent(rotation3, floatValue26, floatValue27);
               invoke4();
               if ((intValue4 & 15) == 0) {
                  invoke16("AI replay: " + intValue4 + "/" + aiRotationTrainerState.items.size());
               }
            }
         }
      }
   }

   private static void invoke4() {
      intValue4++;
      if (intValue4 >= aiRotationTrainerState.items.size()) {
         intValue4 = 0;
         invoke5();
      }
   }

   private static void invoke5() {
      floatValue5 = 0.0F;
      floatValue6 = 0.0F;
      floatValue7 = 0.0F;
      floatValue8 = 0.0F;
   }

   private static float measure(float f, boolean bl) {
      float floatValue30 = (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * f;
      if (bl) {
         floatValue5 = floatValue5 + (floatValue30 - floatValue5) * 0.35F;
         return Mth.clamp(floatValue5, -1.5F, 1.5F);
      } else {
         floatValue6 = floatValue6 + (floatValue30 - floatValue6) * 0.35F;
         return Mth.clamp(floatValue6, -1.5F, 1.5F);
      }
   }

   public static synchronized void invoke6(LivingEntity livingEntity) {
      if (intValue5 != livingEntity.getId()) {
         intValue5 = livingEntity.getId();
         invoke11();
      }

      Vec3 vec3d6 = resolve5(livingEntity);
      Rotation rotation4 = resolve16(vec3d6);
      if (rotation4 != null) {
         float[] aimBase = silentBase();
         float floatValue31 = aimBase[0];
         float floatValue32 = aimBase[1];
         float floatValue33 = Mth.wrapDegrees(rotation4.floatValue - floatValue31);
         float floatValue34 = rotation4.floatValue2 - floatValue32;
         Vec3 vec3d7 = mc().player.getDeltaMovement();
         double doubleValue = Math.hypot(vec3d7.x, vec3d7.z);
         Vec3 vec3d8 = resolve18(livingEntity);
         double doubleValue2 = Math.hypot(vec3d8.x, vec3d8.z);
         double doubleValue3 = mc().player.distanceTo(livingEntity);
         invoke9(
            FLOATS,
            floatValue33,
            floatValue34,
            floatValue9,
            floatValue10,
            floatValue11,
            floatValue12,
            doubleValue3,
            livingEntity.getY() - mc().player.getY(),
            doubleValue,
            doubleValue2,
            vec3d8.x,
            vec3d8.z,
            mc().player.onGround(),
            mc().player.isSprinting(),
            mc().player.getAttackStrengthScale(0.5F),
            livingEntity.hurtTime
         );
         float[] floatValues9 = aiRotationDataset.aiRotationNeuralNetwork.resolve(FLOATS);
         float floatValue35 = floatValues9[0] * 30.0F;
         float floatValue36 = floatValues9[1] * 30.0F;
         float floatValue37 = aiRotationDataset.floatValue > 0.0F ? aiRotationDataset.floatValue : 1.6F;
         float floatValue38 = aiRotationDataset.floatValue2 > floatValue37 ? aiRotationDataset.floatValue2 : floatValue37 + 0.8F;
         int intValue13 = compute(doubleValue3, floatValue37, floatValue38);
         float floatValue39 = aiRotationDataset.measure(intValue13, INTS[intValue13]);
         float floatValue40 = aiRotationDataset.measure2(intValue13, INTS[intValue13]);
         if (aiRotationDataset.compute(intValue13) > 0) {
            INTS[intValue13]++;
         }

         AuraModule auraModule = AuraModule.getInstance();
         float floatValue41 = auraModule == null ? 0.0F : Mth.clamp(auraModule.getAiJitter().getFloat(), 0.0F, 2.0F);
         floatValue13 = floatValue13 + (floatValue39 * floatValue41 - floatValue13) * 0.55F;
         floatValue14 = floatValue14 + (floatValue40 * floatValue41 - floatValue14) * 0.55F;
         float floatValue42 = Mth.clamp(floatValue35 + floatValue13, -35.0F, 35.0F);
         float floatValue43 = Mth.clamp(floatValue36 + floatValue14, -35.0F, 35.0F);
         floatValue42 = measure4(floatValue42, floatValue33);
         floatValue43 = measure4(floatValue43, floatValue34);
         if (auraModule != null && auraModule.getAiHumanMisses().getValue() && aiRotationDataset.floatValue4 > 0.001F) {
            if (intValue8 > 0) {
               floatValue42 = Mth.clamp(floatValue42 + floatValue15, -35.0F, 35.0F);
               floatValue43 = Mth.clamp(floatValue43 + floatValue16, -35.0F, 35.0F);
               intValue8--;
            } else if (ThreadLocalRandom.current().nextFloat() < aiRotationDataset.floatValue4 * 0.015F) {
               float floatValue44 = Math.max(2.0F, aiRotationDataset.floatValue5);
               floatValue15 = (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F) * floatValue44 * 0.5F;
               floatValue16 = (ThreadLocalRandom.current().nextBoolean() ? 1.0F : -1.0F) * floatValue44 * 0.3F;
               intValue8 = ThreadLocalRandom.current().nextInt(2, 5);
            }
         }

         floatValue11 = floatValue9;
         floatValue12 = floatValue10;
         floatValue9 = floatValue42;
         floatValue10 = floatValue43;
         invoke7(floatValue42, floatValue43, true);
         floatValue18 = floatValue33;
         floatValue19 = floatValue34;
         floatValue20 = Math.abs(floatValue13) + Math.abs(floatValue14);
         if ((++intValue11 & 7) == 0) {
            invoke8(
               String.format(
                  Locale.ROOT,
                  "NN err=(%.2f,%.2f) mean=(%.2f,%.2f) jit=(%.2f,%.2f) delta=(%.2f,%.2f) dist=%.2f bucket=%d",
                  floatValue33,
                  floatValue34,
                  floatValue35,
                  floatValue36,
                  floatValue13,
                  floatValue14,
                  floatValue42,
                  floatValue43,
                  doubleValue3,
                  intValue13
               ),
               true
            );
         }

         float floatValue45 = floatValue31 + floatValue42;
         float floatValue46 = Mth.clamp(floatValue32 + floatValue43, -90.0F, 90.0F);
         float floatValue47 = Math.max(0.25F, Math.abs(floatValue42));
         float floatValue48 = Math.max(0.2F, Math.abs(floatValue43));
         pushSilent(new Rotation(floatValue45, floatValue46), floatValue47, floatValue48);
      }
   }

   
   public static synchronized Angle getSilentAngle() {
      return silentReady ? new Angle(silentYaw, silentPitch) : null;
   }

   public static synchronized void clearSilent() {
      silentReady = false;
   }

   private static float[] silentBase() {
      if (!silentReady) {
         Angle cur = AngleConnection.INSTANCE.getCurrentAngle();
         if (cur != null) {
            silentYaw = cur.getYaw();
            silentPitch = cur.getPitch();
         } else if (mc().player != null) {
            silentYaw = mc().player.getYRot();
            silentPitch = mc().player.getXRot();
         }
         silentReady = true;
      }
      return new float[]{silentYaw, silentPitch};
   }

   private static void pushSilent(Rotation target, float maxYaw, float maxPitch) {
      if (target == null) {
         return;
      }
      silentBase();
      float dy = Mth.wrapDegrees(target.floatValue - silentYaw);
      float dp = target.floatValue2 - silentPitch;
      float my = Math.max(0.05F, maxYaw);
      float mp = Math.max(0.05F, maxPitch);
      silentYaw += Mth.clamp(dy, -my, my);
      silentPitch = Mth.clamp(silentPitch + Mth.clamp(dp, -mp, mp), -90.0F, 90.0F);
   }

   private static Vec3 resolve5(LivingEntity livingEntity) {
      Vec3 vec3d9 = PlayerPoseUtils.resolve4(livingEntity.getBoundingBox(), false);
      return vec3d9 != null ? vec3d9 : resolve17(livingEntity.getBoundingBox(), livingEntity.getBoundingBox().getCenter());
   }

   private static float[] resolve6(float[] fs, int i) {
      int intValue14 = fs.length;
      float[] floatValues10 = new float[intValue14];

      for (int intValue15 = 0; intValue15 < intValue14; intValue15++) {
         int intValue16 = Math.max(0, intValue15 - i);
         int intValue17 = Math.min(intValue14 - 1, intValue15 + i);
         float floatValue49 = 0.0F;

         for (int intValue18 = intValue16; intValue18 <= intValue17; intValue18++) {
            floatValue49 += fs[intValue18];
         }

         floatValues10[intValue15] = floatValue49 / (intValue17 - intValue16 + 1);
      }

      return floatValues10;
   }

   private static int compute(double d, float f, float g) {
      if (d < f) {
         return 0;
      } else {
         return d < g ? 1 : 2;
      }
   }

   private static float measure2(float[] fs, float f) {
      if (fs.length == 0) {
         return 0.0F;
      } else {
         int intValue19 = Mth.clamp((int)(f * fs.length), 0, fs.length - 1);
         return fs[intValue19];
      }
   }

   private static float measure3() {
      try {
         return mc().options.sensitivity().get().floatValue();
      } catch (Throwable exception) {
         return -1.0F;
      }
   }

   private static int compute2(float f, float g, int i) {
      float floatValue50 = (f + g) / (2.0F * g);
      return Mth.clamp((int)(floatValue50 * i), 0, i - 1);
   }

   public static synchronized AiRotationTelemetry resolve7() {
      AiRotationTelemetry aiRotationTelemetry = new AiRotationTelemetry();
      aiRotationTelemetry.text = defaultValue;
      AiRotationTrainer.AiRotationTrainerState aiRotationTrainerState3 = resolve29();
      if (aiRotationTrainerState3 != null && aiRotationTrainerState3.items != null && aiRotationTrainerState3.items.size() >= 4) {
         invoke15(aiRotationTrainerState3);
         List items2 = aiRotationTrainerState3.items;
         int intValue20 = items2.size();
         aiRotationTelemetry.intValue = intValue20;
         aiRotationTelemetry.intValue2 = compute5(items2);
         aiRotationTelemetry.floatValue2 = aiRotationTrainerState3.floatValue;
         int intValue21 = 0;

         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState211 : (List<AiRotationTrainer.AiRotationTrainerState2>)items2) {
            if (aiRotationTrainerState211 != null && aiRotationTrainerState211.flag9 && aiRotationTrainerState211.flag10) {
               intValue21++;
            }
         }

         aiRotationTelemetry.intValue3 = intValue21;
         aiRotationTelemetry.intValue4 = Math.max(0, aiRotationTelemetry.intValue2 - intValue21);
         aiRotationTelemetry.floatValue = aiRotationTelemetry.intValue2 > 0 ? (float)aiRotationTelemetry.intValue4 / aiRotationTelemetry.intValue2 : 0.0F;
         float[] floatValues11 = new float[intValue20];
         int intValue22 = 0;
         float floatValue51 = Float.MAX_VALUE;
         float floatValue52 = 0.0F;

         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState212 : (List<AiRotationTrainer.AiRotationTrainerState2>)items2) {
            if (aiRotationTrainerState212 != null) {
               float floatValue53 = (float)aiRotationTrainerState212.doubleValue9;
               floatValues11[intValue22++] = floatValue53;
               if (floatValue53 < floatValue51) {
                  floatValue51 = floatValue53;
               }

               if (floatValue53 > floatValue52) {
                  floatValue52 = floatValue53;
               }
            }
         }

         float[] floatValues12 = Arrays.copyOf(floatValues11, intValue22);
         Arrays.sort(floatValues12);
         aiRotationTelemetry.floatValue5 = measure2(floatValues12, 0.34F);
         aiRotationTelemetry.floatValue6 = measure2(floatValues12, 0.67F);
         if (aiRotationTelemetry.floatValue6 <= aiRotationTelemetry.floatValue5) {
            aiRotationTelemetry.floatValue6 = aiRotationTelemetry.floatValue5 + 0.5F;
         }

         aiRotationTelemetry.floatValue3 = floatValue51 == Float.MAX_VALUE ? 0.0F : floatValue51;
         aiRotationTelemetry.floatValue4 = floatValue52;
         byte byteValue = 20;
         aiRotationTelemetry.intValue5 = byteValue;
         float[] floatValues13 = new float[byteValue];
         int[] intValues = new int[byteValue];
         byte byteValue2 = 21;
         aiRotationTelemetry.ints2 = new int[byteValue2];
         aiRotationTelemetry.ints3 = new int[byteValue2];
         aiRotationTelemetry.floatValue8 = 25.0F;
         float floatValue54 = aiRotationTelemetry.floatValue4 - aiRotationTelemetry.floatValue3;
         if (floatValue54 < 0.001F) {
            floatValue54 = 1.0F;
         }

         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState213 : (List<AiRotationTrainer.AiRotationTrainerState2>)items2) {
            if (aiRotationTrainerState213 != null) {
               int intValue23 = compute(aiRotationTrainerState213.doubleValue9, aiRotationTelemetry.floatValue5, aiRotationTelemetry.floatValue6);
               aiRotationTelemetry.ints[intValue23]++;
               float floatValue55 = Math.abs(aiRotationTrainerState213.floatValue7) + Math.abs(aiRotationTrainerState213.floatValue8);
               int intValue24 = Mth.clamp((int)(((float)aiRotationTrainerState213.doubleValue9 - aiRotationTelemetry.floatValue3) / floatValue54 * byteValue), 0, byteValue - 1);
               floatValues13[intValue24] += floatValue55;
               intValues[intValue24]++;
               aiRotationTelemetry.ints2[compute2(aiRotationTrainerState213.floatValue7, aiRotationTelemetry.floatValue8, byteValue2)]++;
               aiRotationTelemetry.ints3[compute2(aiRotationTrainerState213.floatValue8, aiRotationTelemetry.floatValue8, byteValue2)]++;
               if (Math.abs(aiRotationTrainerState213.floatValue7) > 8.0F) {
                  aiRotationTelemetry.intValue8++;
               } else {
                  aiRotationTelemetry.intValue9++;
               }
            }
         }

         aiRotationTelemetry.floats = new float[byteValue];
         float floatValue56 = 0.0F;

         for (int intValue25 = 0; intValue25 < byteValue; intValue25++) {
            aiRotationTelemetry.floats[intValue25] = intValues[intValue25] > 0 ? floatValues13[intValue25] / intValues[intValue25] : 0.0F;
            if (aiRotationTelemetry.floats[intValue25] > floatValue56) {
               floatValue56 = aiRotationTelemetry.floats[intValue25];
            }
         }

         aiRotationTelemetry.floatValue7 = floatValue56;
         int intValue26 = 1;
         int intValue27 = 1;

         for (int intValue28 = 0; intValue28 < byteValue2; intValue28++) {
            if (aiRotationTelemetry.ints2[intValue28] > intValue26) {
               intValue26 = aiRotationTelemetry.ints2[intValue28];
            }

            if (aiRotationTelemetry.ints3[intValue28] > intValue27) {
               intValue27 = aiRotationTelemetry.ints3[intValue28];
            }
         }

         aiRotationTelemetry.intValue6 = intValue26;
         aiRotationTelemetry.intValue7 = intValue27;
         float[] floatValues14 = new float[intValue20];
         float[] floatValues15 = new float[intValue20];

         for (int intValue29 = 0; intValue29 < intValue20; intValue29++) {
            AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState214 = (AiRotationTrainer.AiRotationTrainerState2)items2.get(intValue29);
            floatValues14[intValue29] = aiRotationTrainerState214 == null ? 0.0F : aiRotationTrainerState214.floatValue7;
            floatValues15[intValue29] = aiRotationTrainerState214 == null ? 0.0F : aiRotationTrainerState214.floatValue8;
         }

         aiRotationTelemetry.floats2 = resolve9(floatValues14, 160);
         aiRotationTelemetry.floats3 = resolve9(floatValues15, 160);
         AiRotationDataset aiRotationDataset2 = AiRotationDataset.resolve(resolve22());
         if (aiRotationDataset2 != null && aiRotationDataset2.check(16, 2)) {
            aiRotationTelemetry.flag2 = true;
            aiRotationTelemetry.floatValue9 = floatValue17;
            float[] floatValues16 = new float[intValue20];
            float[] floatValues17 = new float[intValue20];
            float[] floatValues18 = new float[16];

            for (int intValue30 = 0; intValue30 < intValue20 - 1; intValue30++) {
               AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState215 = (AiRotationTrainer.AiRotationTrainerState2)items2.get(intValue30);
               if (aiRotationTrainerState215 != null) {
                  float floatValue57 = Mth.wrapDegrees(aiRotationTrainerState215.floatValue3 - aiRotationTrainerState215.floatValue);
                  float floatValue58 = aiRotationTrainerState215.floatValue4 - aiRotationTrainerState215.floatValue2;
                  float floatValue59 = 0.0F;
                  float floatValue60 = 0.0F;
                  if (intValue30 >= 1) {
                     AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState216 = (AiRotationTrainer.AiRotationTrainerState2)items2.get(intValue30 - 1);
                     if (aiRotationTrainerState216 != null) {
                        floatValue59 = aiRotationTrainerState216.floatValue7;
                        floatValue60 = aiRotationTrainerState216.floatValue8;
                     }
                  }

                  invoke9(
                     floatValues18,
                     floatValue57,
                     floatValue58,
                     aiRotationTrainerState215.floatValue7,
                     aiRotationTrainerState215.floatValue8,
                     floatValue59,
                     floatValue60,
                     aiRotationTrainerState215.doubleValue9,
                     aiRotationTrainerState215.doubleValue10,
                     aiRotationTrainerState215.doubleValue7,
                     aiRotationTrainerState215.doubleValue14,
                     aiRotationTrainerState215.doubleValue11,
                     aiRotationTrainerState215.doubleValue13,
                     aiRotationTrainerState215.flag8,
                     aiRotationTrainerState215.flag7,
                     aiRotationTrainerState215.floatValue13,
                     aiRotationTrainerState215.intValue3
                  );
                  float[] floatValues19 = aiRotationDataset2.aiRotationNeuralNetwork.resolve(floatValues18);
                  floatValues16[intValue30] = floatValues19[0] * 30.0F;
                  floatValues17[intValue30] = floatValues19[1] * 30.0F;
               }
            }

            aiRotationTelemetry.floats4 = resolve9(floatValues16, 160);
            aiRotationTelemetry.floats5 = resolve9(floatValues17, 160);
         }

         aiRotationTelemetry.flag = true;
         return aiRotationTelemetry;
      } else {
         aiRotationTelemetry.flag = false;
         return aiRotationTelemetry;
      }
   }

   private static float measure4(float f, float g) {
      if (Math.abs(g) < 18.0F) {
         return f;
      } else {
         boolean flag4 = Math.signum(f) != Math.signum(g);
         boolean flag5 = Math.abs(f) < 1.0F;
         return !flag4 && !flag5 ? f : Mth.clamp(g * 0.5F, -35.0F, 35.0F);
      }
   }

   private static float[][] resolve8(List<List<Float>> list) {
      float[][] floatValuesValues5 = new float[list.size()][];

      for (int intValue31 = 0; intValue31 < list.size(); intValue31++) {
         List items3 = (List)list.get(intValue31);
         float[] floatValues20 = new float[items3.size()];

         for (int intValue32 = 0; intValue32 < floatValues20.length; intValue32++) {
            floatValues20[intValue32] = (Float)items3.get(intValue32);
         }

         floatValuesValues5[intValue31] = floatValues20;
      }

      return floatValuesValues5;
   }

   private static float[] resolve9(float[] fs, int i) {
      float[] floatValues21 = new float[i];
      int intValue33 = fs.length;
      if (intValue33 == 0) {
         return floatValues21;
      } else {
         for (int intValue34 = 0; intValue34 < i; intValue34++) {
            int intValue35 = (int)((long)intValue34 * intValue33 / i);
            if (intValue35 >= intValue33) {
               intValue35 = intValue33 - 1;
            }

            floatValues21[intValue34] = fs[intValue35];
         }

         return floatValues21;
      }
   }

   private static void invoke7(float f, float g, boolean bl) {
      FLOATS_2[intValue9] = f;
      FLOATS_3[intValue9] = g;
      intValue9 = (intValue9 + 1) % 160;
      flag7 = bl;
   }

   public static int compute3() {
      return 160;
   }

   public static float[] getFLOATS_2() {
      return FLOATS_2;
   }

   public static float[] getFLOATS_3() {
      return FLOATS_3;
   }

   public static int getIntValue9() {
      return intValue9;
   }

   public static boolean isFlag7() {
      return flag7;
   }

   public static float[] getFloats() {
      return floats;
   }

   public static float[] getFloats2() {
      return floats2;
   }

   public static float getFloatValue17() {
      return floatValue17;
   }

   public static int getIntValue10() {
      return intValue10;
   }

   public static float getFloatValue18() {
      return floatValue18;
   }

   public static float getFloatValue19() {
      return floatValue19;
   }

   public static float getFloatValue20() {
      return floatValue20;
   }

   public static boolean check() {
      return flag5 && aiRotationDataset != null;
   }

   public static boolean check2() {
      return isDebugLogEnabled();
   }

   
   private static boolean isDebugLogEnabled() {
      AuraModule auraModule = AuraModule.getInstance();
      return auraModule != null && auraModule.getAiDebugLog().getValue();
   }

   public static Path resolve10() {
      return resolve13().resolve("logs").resolve(resolve24(defaultValue) + ".log");
   }

   private static void invoke8(String string, boolean bl) {
      if (isDebugLogEnabled()) {
         long longValue3 = System.currentTimeMillis();
         String text2 = "[AI] " + string;

         try {
            Path path3 = resolve10();
            Files.createDirectories(path3.getParent());
            Files.writeString(path3, longValue3 + " " + text2 + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (Throwable exception2) {
         }

         if (bl && longValue3 - timestamp5 >= 1500L) {
            timestamp5 = longValue3;
            ChatMessage.brandmessage(text2);
         }
      }
   }

   private static void invoke9(
      float[] fs,
      float f,
      float g,
      float h,
      float i,
      float j,
      float k,
      double d,
      double e,
      double l,
      double m,
      double n,
      double o,
      boolean bl,
      boolean bl2,
      float p,
      float q
   ) {
      fs[0] = Mth.clamp(f / 180.0F, -1.0F, 1.0F);
      fs[1] = Mth.clamp(g / 90.0F, -1.0F, 1.0F);
      fs[2] = Mth.clamp(h / 30.0F, -1.0F, 1.0F);
      fs[3] = Mth.clamp(i / 30.0F, -1.0F, 1.0F);
      fs[4] = Mth.clamp(j / 30.0F, -1.0F, 1.0F);
      fs[5] = Mth.clamp(k / 30.0F, -1.0F, 1.0F);
      fs[6] = Mth.clamp((float)(d / 6.0), 0.0F, 1.5F);
      fs[7] = Mth.clamp((float)(e / 3.0), -1.0F, 1.0F);
      fs[8] = Mth.clamp((float)(l / 0.6F), 0.0F, 1.5F);
      fs[9] = Mth.clamp((float)(m / 0.6F), 0.0F, 1.5F);
      fs[10] = Mth.clamp((float)(n / 0.6F), -1.5F, 1.5F);
      fs[11] = Mth.clamp((float)(o / 0.6F), -1.5F, 1.5F);
      fs[12] = bl ? 1.0F : 0.0F;
      fs[13] = bl2 ? 1.0F : 0.0F;
      fs[14] = Mth.clamp(p, 0.0F, 1.0F);
      fs[15] = Mth.clamp(q / 10.0F, 0.0F, 1.0F);
   }

   public static synchronized boolean check3() {
      if (!flag2 || flag) {
         return false;
      } else if (flag5 && aiRotationDataset != null) {
         return mc().player != null && mc().player.getAttackStrengthScale(0.0F) >= 0.9F;
      } else if (aiRotationTrainerState2 == null) {
         return false;
      } else {
         return intValue6 == 0
            ? mc().player != null && mc().player.getAttackStrengthScale(0.0F) >= 0.92F
            : flag4 && System.currentTimeMillis() >= timestamp4;
      }
   }

   public static synchronized void invoke10() {
      intValue4 = 0;
      intValue5 = Integer.MIN_VALUE;
      flag4 = false;
      timestamp3 = 0L;
      timestamp4 = 0L;
      aiRotationTrainerState2 = null;
      clearSilent();
      invoke11();
      invoke5();
   }

   private static void invoke11() {
      floatValue9 = 0.0F;
      floatValue10 = 0.0F;
      floatValue11 = 0.0F;
      floatValue12 = 0.0F;
      floatValue13 = 0.0F;
      floatValue14 = 0.0F;
      intValue8 = 0;
      floatValue15 = 0.0F;
      floatValue16 = 0.0F;

      for (int intValue36 = 0; intValue36 < INTS.length; intValue36++) {
         INTS[intValue36] = 0;
      }
   }

   public static synchronized void invoke12() {
      if (flag && aiRotationTrainerState.items != null && !aiRotationTrainerState.items.isEmpty()) {
         check6();
      }

      flag = false;
      flag2 = false;
   }

   public static boolean isFlag() {
      return flag;
   }

   public static boolean isFlag2() {
      return flag2;
   }

   public static AiRotationStatus resolve11() {
      return ATOMIC_REFERENCE.get();
   }

   public static String resolve12() {
      return ATOMIC_REFERENCE.get().text();
   }

   public static void invoke13(Consumer<AiRotationStatus> consumer) {
      if (consumer != null) {
         COPY_ON_WRITE_ARRAY_LIST.add(consumer);
         consumer.accept(ATOMIC_REFERENCE.get());
      }
   }

   public static void invoke14(Consumer<AiRotationStatus> consumer) {
      COPY_ON_WRITE_ARRAY_LIST.remove(consumer);
   }

   public static Path resolve13() {
      Minecraft client = Minecraft.getInstance();
      return client.gameDirectory.toPath().resolve("polaris").resolve("AI");
   }

   private static Vec3 resolve14(LivingEntity livingEntity, float f, float g) {
      Vec3 vec3d10 = mc().player.getEyePosition();
      Vec3 vec3d11 = EntityRaycastUtils.resolve4(g, f);
      AABB box3 = livingEntity.getBoundingBox();
      Optional<Vec3> optional = box3.inflate(0.05).clip(vec3d10, vec3d10.add(vec3d11.scale(8.0)));
      if (optional.isPresent()) {
         return resolve17(box3, (Vec3)optional.get());
      } else {
         Vec3 vec3d12 = box3.getCenter();
         double doubleValue4 = Math.max(0.1, vec3d12.subtract(vec3d10).dot(vec3d11));
         Vec3 vec3d13 = vec3d10.add(vec3d11.scale(doubleValue4));
         return resolve17(box3, vec3d13);
      }
   }

   private static Vec3 resolve15(LivingEntity livingEntity, AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState217) {
      AABB box4 = livingEntity.getBoundingBox();
      double doubleValue5;
      double doubleValue6;
      double doubleValue7;
      if (aiRotationTrainerState217.flag) {
         doubleValue5 = measure7(aiRotationTrainerState217.doubleValue, 0.14);
         doubleValue6 = measure7(aiRotationTrainerState217.doubleValue2, 0.08);
         doubleValue7 = measure7(aiRotationTrainerState217.doubleValue3, 0.14);
      } else {
         doubleValue5 = 0.5;
         doubleValue6 = Mth.clamp(0.5 + aiRotationTrainerState217.floatValue6 / 180.0, 0.25, 0.75);
         doubleValue7 = 0.5;
      }

      Vec3 vec3d14 = resolve18(livingEntity);
      double doubleValue8 = Mth.clamp(mc().player.distanceTo(livingEntity) / 4.0, 0.25, 0.85);
      doubleValue5 += vec3d14.x * doubleValue8 / Math.max(0.01, box4.getXsize());
      doubleValue6 += vec3d14.y * doubleValue8 / Math.max(0.01, box4.getYsize());
      doubleValue7 += vec3d14.z * doubleValue8 / Math.max(0.01, box4.getZsize());
      doubleValue5 = measure7(doubleValue5, 0.14);
      doubleValue6 = measure7(doubleValue6, 0.08);
      doubleValue7 = measure7(doubleValue7, 0.14);
      return new Vec3(Mth.lerp(doubleValue5, box4.minX, box4.maxX), Mth.lerp(doubleValue6, box4.minY, box4.maxY), Mth.lerp(doubleValue7, box4.minZ, box4.maxZ));
   }

   private static Rotation resolve16(Vec3 Vec3) {
      if (Vec3 != null && mc().player != null) {
         Vec3 vec3d15 = Vec3.subtract(mc().player.getEyePosition());
         if (vec3d15.lengthSqr() < 1.0E-8) {
            return null;
         } else {
            float floatValue61 = (float)Math.toDegrees(Math.atan2(-vec3d15.x, vec3d15.z));
            float floatValue62 = (float)Mth.clamp(-Math.toDegrees(Math.atan2(vec3d15.y, Math.hypot(vec3d15.x, vec3d15.z))), -90.0, 90.0);
            return new Rotation(floatValue61, floatValue62);
         }
      } else {
         return null;
      }
   }

   private static Vec3 resolve17(AABB AABB, Vec3 Vec3) {
      return new Vec3(measure5(Vec3.x, AABB.minX, AABB.maxX, 0.14), measure5(Vec3.y, AABB.minY, AABB.maxY, 0.08), measure5(Vec3.z, AABB.minZ, AABB.maxZ, 0.14));
   }

   private static double measure5(double d, double e, double f, double g) {
      double doubleValue9 = f - e;
      if (doubleValue9 <= 1.0E-6) {
         return e;
      } else {
         double doubleValue10 = doubleValue9 * g;
         return Mth.clamp(d, e + doubleValue10, f - doubleValue10);
      }
   }

   private static double measure6(double d, double e, double f, double g) {
      double doubleValue11 = f - e;
      return doubleValue11 <= 1.0E-6 ? 0.5 : measure7((d - e) / doubleValue11, g);
   }

   private static double measure7(double d, double e) {
      return Mth.clamp(d, e, 1.0 - e);
   }

   private static Vec3 resolve18(LivingEntity livingEntity) {
      Vec3 vec3d16 = livingEntity.getDeltaMovement();
      Vec3 vec3d17 = new Vec3(livingEntity.getX() - livingEntity.xo, livingEntity.getY() - livingEntity.yo, livingEntity.getZ() - livingEntity.zo);
      return vec3d17.lengthSqr() > vec3d16.lengthSqr() ? vec3d17 : vec3d16;
   }

   private static boolean check4(float f, float g) {
      return Math.abs(f) > 0.02F && Math.abs(g) > 0.02F && Math.signum(f) != Math.signum(g);
   }

   private static float measure8(float f, boolean bl) {
      float floatValue63 = Math.abs(f);
      return bl ? 0.0F : floatValue63;
   }

   private static long compute4(AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState218) {
      if (aiRotationTrainerState218.timestamp > 0L) {
         return aiRotationTrainerState218.timestamp;
      } else {
         return aiRotationTrainerState218.intValue2 > 0 ? aiRotationTrainerState218.intValue2 * 50L : 0L;
      }
   }

   private static Path resolve19() {
      return resolve13().resolve("profiles");
   }

   private static Path resolve20() {
      return resolve19().resolve(resolve24(defaultValue) + ".json");
   }

   private static Path resolve21() {
      return resolve13().resolve("models");
   }

   private static Path resolve22() {
      return resolve21().resolve(resolve24(defaultValue) + ".json");
   }

   public static boolean check5() {
      return Files.isRegularFile(resolve22());
   }

   public static boolean isFlag6() {
      return flag6;
   }

   private static Path resolve23() {
      return resolve13().resolve("rotation_pattern.json");
   }

   static String resolve24(String string) {
      String text3 = string != null && !string.isBlank() ? string.trim() : "default";
      text3 = text3.replace('\\', '/');
      int intValue37 = text3.lastIndexOf(47);
      if (intValue37 >= 0) {
         text3 = text3.substring(intValue37 + 1);
      }

      if (text3.endsWith(".json")) {
         text3 = text3.substring(0, text3.length() - 5);
      }

      text3 = text3.replaceAll("[^a-zA-Z0-9._-]", "_");
      if (text3.isBlank() || text3.equals(".") || text3.equals("..")) {
         text3 = "default";
      }

      return text3;
   }

   private static String resolve25(String string) {
      return string != null && string.endsWith(".json") ? string.substring(0, string.length() - 5) : string;
   }

   public static String getDefaultValue() {
      return defaultValue;
   }

   public static synchronized String resolve26(String string) {
      if (flag) {
         return "Нельзя менять профиль во время записи (.ai stop сначала).";
      } else {
         defaultValue = resolve24(string);
         ensureProfileExists(defaultValue);
         invoke16("AI profile: " + defaultValue);
         return "Активный профиль: " + defaultValue;
      }
   }

   
   public static synchronized String createProfile(String name) {
      if (flag) {
         return "Нельзя создавать профиль во время записи (.ai stop сначала).";
      }
      String id = resolve24(name);
      if (!ensureProfileExists(id)) {
         return "Не удалось создать профиль '" + id + "'.";
      }
      defaultValue = id;
      invoke16("AI profile created: " + defaultValue);
      return "Профиль '" + defaultValue + "' создан и выбран.";
   }

   
   public static synchronized String createNextProfile() {
      List<String> existing = resolve27();
      String base = "profile";
      String candidate = base;
      int n = 2;
      while (true) {
         final String check = candidate;
         boolean taken = false;
         for (String p : existing) {
            if (p != null && p.equalsIgnoreCase(check)) {
               taken = true;
               break;
            }
         }
         if (!taken) {
            break;
         }
         candidate = base + n;
         n++;
         if (n > 999) {
            candidate = "profile_" + (System.currentTimeMillis() % 100000L);
            break;
         }
      }
      return createProfile(candidate);
   }

   public static synchronized String deleteProfile(String name) {
      if (flag) {
         return "Нельзя удалять профиль во время записи.";
      }
      String id = resolve24(name);
      if ("default".equals(id)) {
         return "Профиль default нельзя удалить.";
      }
      try {
         Path pattern = resolve19().resolve(id + ".json");
         Path model = resolve21().resolve(id + ".json");
         Files.deleteIfExists(pattern);
         Files.deleteIfExists(model);
         if (id.equals(defaultValue)) {
            defaultValue = "default";
            ensureProfileExists("default");
         }
         return "Профиль '" + id + "' удалён.";
      } catch (Throwable t) {
         return "Не удалось удалить профиль '" + id + "'.";
      }
   }

   private static boolean ensureProfileExists(String id) {
      try {
         Path path = resolve19().resolve(resolve24(id) + ".json");
         Files.createDirectories(path.getParent());
         if (!Files.isRegularFile(path)) {
            AiRotationTrainerState empty = new AiRotationTrainerState();
            empty.intValue = 3;
            empty.items = new ArrayList<>();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
               GSON.toJson(empty, writer);
            }
         }
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   public static List<String> resolve27() {
      ArrayList<String> arrayList5 = new ArrayList<>();
      arrayList5.add("default");
      if (defaultValue != null && !arrayList5.contains(defaultValue)) {
         arrayList5.add(defaultValue);
      }

      try {
         Path path4 = resolve19();
         if (Files.isDirectory(path4)) {
            try (Stream stream = Files.list(path4)) {
               ((Stream<Path>)stream).filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                  .forEach(path -> {
                     String name = resolve25(path.getFileName().toString());
                     if (!arrayList5.contains(name)) {
                        arrayList5.add(name);
                     }
                  });
            }
         }
         Path models = resolve21();
         if (Files.isDirectory(models)) {
            try (Stream stream = Files.list(models)) {
               ((Stream<Path>)stream).filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                  .forEach(path -> {
                     String name = resolve25(path.getFileName().toString());
                     if (!arrayList5.contains(name)) {
                        arrayList5.add(name);
                     }
                  });
            }
         }
      } catch (Throwable exception3) {
      }

      arrayList5.sort(String::compareToIgnoreCase);
      return arrayList5;
   }

   public static synchronized String resolve28() {
      List items4 = resolve27();
      return items4.isEmpty()
         ? "Профили не найдены. Активный: " + defaultValue
         : "Профили (" + items4.size() + "): " + String.join(", ", items4) + " | активный: " + defaultValue;
   }

   private static boolean check6() {
      try {
         invoke15(aiRotationTrainerState);
         Path path5 = resolve20();
         Files.createDirectories(path5.getParent());

         try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path5, StandardCharsets.UTF_8)) {
            GSON.toJson(aiRotationTrainerState, bufferedWriter);
         }

         return true;
      } catch (Throwable exception4) {
         return false;
      }
   }

   private static AiRotationTrainer.AiRotationTrainerState resolve29() {
      try {
         Path path6 = resolve20();
         if (!Files.isRegularFile(path6)) {
            Path path7 = resolve23();
            if (!"default".equals(resolve24(defaultValue)) || !Files.isRegularFile(path7)) {
               return null;
            }

            path6 = path7;
         }

         AiRotationTrainer.AiRotationTrainerState aiRotationTrainerState4;
         try (BufferedReader bufferedReader = Files.newBufferedReader(path6, StandardCharsets.UTF_8)) {
            aiRotationTrainerState4 = (AiRotationTrainer.AiRotationTrainerState)GSON.fromJson(bufferedReader, AiRotationTrainer.AiRotationTrainerState.class);
         }

         return aiRotationTrainerState4;
      } catch (Throwable exception5) {
         return null;
      }
   }

   private static void invoke15(AiRotationTrainer.AiRotationTrainerState aiRotationTrainerState5) {
      int intValue38 = aiRotationTrainerState5.intValue;
      aiRotationTrainerState5.intValue = 3;
      if (aiRotationTrainerState5.items == null) {
         aiRotationTrainerState5.items = new ArrayList<>();
      }

      if (intValue38 < 2) {
         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState219 : aiRotationTrainerState5.items) {
            if (aiRotationTrainerState219 != null) {
               aiRotationTrainerState219.flag = false;
            }
         }
      }

      if (intValue38 < 3) {
         float floatValue64 = 0.0F;
         float floatValue65 = 0.0F;
         double doubleValue12 = 0.0;

         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState220 : aiRotationTrainerState5.items) {
            if (aiRotationTrainerState220 != null) {
               aiRotationTrainerState220.floatValue9 = aiRotationTrainerState220.floatValue7 - floatValue64;
               aiRotationTrainerState220.floatValue10 = aiRotationTrainerState220.floatValue8 - floatValue65;
               aiRotationTrainerState220.doubleValue8 = aiRotationTrainerState220.doubleValue5 - doubleValue12;
               aiRotationTrainerState220.flag2 = check4(floatValue64, aiRotationTrainerState220.floatValue7);
               aiRotationTrainerState220.flag3 = check4(floatValue65, aiRotationTrainerState220.floatValue8);
               aiRotationTrainerState220.flag4 = Math.abs(aiRotationTrainerState220.floatValue7) < 0.035F && Math.abs(aiRotationTrainerState220.floatValue8) < 0.035F;
               if (aiRotationTrainerState220.timestamp <= 0L && aiRotationTrainerState220.intValue2 > 0) {
                  aiRotationTrainerState220.timestamp = aiRotationTrainerState220.intValue2 * 50L;
               }

               floatValue64 = aiRotationTrainerState220.floatValue7;
               floatValue65 = aiRotationTrainerState220.floatValue8;
               doubleValue12 = aiRotationTrainerState220.doubleValue5;
            }
         }
      }

      aiRotationTrainerState5.intValue2 = aiRotationTrainerState5.items.size();
      aiRotationTrainerState5.intValue3 = compute5(aiRotationTrainerState5.items);
   }

   private static int compute5(List<AiRotationTrainer.AiRotationTrainerState2> list) {
      int intValue39 = 0;
      if (list != null) {
         for (AiRotationTrainer.AiRotationTrainerState2 aiRotationTrainerState221 : list) {
            if (aiRotationTrainerState221 != null && aiRotationTrainerState221.flag9) {
               intValue39++;
            }
         }
      }

      return intValue39;
   }

   private static void invoke16(String string) {
      AiRotationTrainer.AiRotationTrainerState state = aiRotationTrainerState;
      long longValue4;
      try {
         longValue4 = state == null || state.items == null ? 0L : state.items.size();
      } catch (RuntimeException exception7) {
         
         
         longValue4 = 0L;
      }

      AiRotationStatus aiRotationStatus = new AiRotationStatus(string, flag, flag6, longValue4, intValue7, 0L, System.currentTimeMillis());
      ATOMIC_REFERENCE.set(aiRotationStatus);
      notifyStatusListeners(aiRotationStatus);
   }

   
   private static void notifyStatusListeners(AiRotationStatus status) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && !minecraft.isSameThread()) {
         minecraft.execute(() -> dispatchStatusListeners(status));
         return;
      }
      dispatchStatusListeners(status);
   }

   private static void dispatchStatusListeners(AiRotationStatus status) {
      for (Consumer consumer2 : COPY_ON_WRITE_ARRAY_LIST) {
         try {
            consumer2.accept(status);
         } catch (Throwable exception6) {
         }
      }
   }

   static final class AiRotationTrainerState {
      int intValue = 3;
      long timestamp;
      int intValue2;
      int intValue3;
      float floatValue;
      List<AiRotationTrainer.AiRotationTrainerState2> items = new ArrayList<>();
   }

   static final class AiRotationTrainerState2 {
      int intValue;
      float floatValue;
      float floatValue2;
      float floatValue3;
      float floatValue4;
      float floatValue5;
      float floatValue6;
      double doubleValue;
      double doubleValue2;
      double doubleValue3;
      boolean flag;
      float floatValue7;
      float floatValue8;
      float floatValue9;
      float floatValue10;
      boolean flag2;
      boolean flag3;
      boolean flag4;
      float floatValue11;
      float floatValue12;
      boolean flag5;
      boolean flag6;
      boolean flag7;
      boolean flag8;
      double doubleValue4;
      double doubleValue5;
      double doubleValue6;
      double doubleValue7;
      double doubleValue8;
      double doubleValue9;
      double doubleValue10;
      double doubleValue11;
      double doubleValue12;
      double doubleValue13;
      double doubleValue14;
      boolean flag9;
      boolean flag10;
      int intValue2;
      long timestamp;
      float floatValue13;
      boolean flag11;
      float floatValue14;
      int intValue3;
   }
}
