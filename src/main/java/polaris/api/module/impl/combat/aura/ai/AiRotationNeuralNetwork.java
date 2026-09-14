package polaris.api.module.impl.combat.aura.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public final class AiRotationNeuralNetwork {
   private static final Gson GSON = new GsonBuilder().create();
   private static final float FLOAT_VALUE = 0.9F;
   private static final float FLOAT_VALUE_2 = 0.999F;
   private static final float FLOAT_VALUE_3 = 1.0E-8F;
   public int[] ints;
   public float[][][] floats;
   public float[][] floats2;
   private transient float[][] floats3;
   private transient float[][] floats4;
   private transient float[][][] floats5;
   private transient float[][][] floats6;
   private transient float[][] floats7;
   private transient float[][] floats8;
   private transient int intValue;
   private transient float floatValue;
   private transient float floatValue2;

   public AiRotationNeuralNetwork() {
   }

   public AiRotationNeuralNetwork(int... is) {
      this.ints = (int[])is.clone();
      int intValue = is.length - 1;
      this.floats = new float[intValue][][];
      this.floats2 = new float[intValue][];

      for (int intValue2 = 0; intValue2 < intValue; intValue2++) {
         int intValue3 = is[intValue2];
         int intValue4 = is[intValue2 + 1];
         this.floats[intValue2] = new float[intValue4][intValue3];
         this.floats2[intValue2] = new float[intValue4];
         float floatValue = (float)Math.sqrt(6.0 / (intValue3 + intValue4));

         for (int intValue5 = 0; intValue5 < intValue4; intValue5++) {
            for (int intValue6 = 0; intValue6 < intValue3; intValue6++) {
               this.floats[intValue2][intValue5][intValue6] = (ThreadLocalRandom.current().nextFloat() * 2.0F - 1.0F) * floatValue;
            }
         }
      }
   }

   public boolean check() {
      return this.ints != null && this.ints.length >= 2 && this.floats != null && this.floats2 != null;
   }

   public boolean check2(int i, int j) {
      return this.check() && this.ints[0] == i && this.ints[this.ints.length - 1] == j;
   }

   private void invoke() {
      if (this.floats3 == null) {
         this.floats3 = new float[this.ints.length][];

         for (int intValue7 = 0; intValue7 < this.ints.length; intValue7++) {
            this.floats3[intValue7] = new float[this.ints[intValue7]];
         }
      }
   }

   public float[] resolve(float[] fs) {
      this.invoke();
      System.arraycopy(fs, 0, this.floats3[0], 0, this.ints[0]);
      int intValue8 = this.floats.length;

      for (int intValue9 = 0; intValue9 < intValue8; intValue9++) {
         float[] floatValues = this.floats3[intValue9];
         float[] floatValues2 = this.floats3[intValue9 + 1];
         float[][] floatValuesValues = this.floats[intValue9];
         float[] floatValues3 = this.floats2[intValue9];
         boolean flag = intValue9 == intValue8 - 1;

         for (int intValue10 = 0; intValue10 < floatValues2.length; intValue10++) {
            float floatValue2 = floatValues3[intValue10];
            float[] floatValues4 = floatValuesValues[intValue10];

            for (int intValue11 = 0; intValue11 < floatValues.length; intValue11++) {
               floatValue2 += floatValues4[intValue11] * floatValues[intValue11];
            }

            floatValues2[intValue10] = flag ? floatValue2 : (float)Math.tanh(floatValue2);
         }
      }

      return this.floats3[this.ints.length - 1];
   }

   public void invoke2(float[][] fs, float[][] gs, int i, float f) {
      this.invoke();
      this.invoke3();
      int intValue12 = fs.length;
      int[] intValues = new int[intValue12];
      int intValue13 = 0;

      while (intValue13 < intValue12) {
         intValues[intValue13] = intValue13++;
      }

      intValue13 = this.floats.length;

      for (int intValue14 = 0; intValue14 < i; intValue14++) {
         invoke4(intValues);

         for (int intValue15 = 0; intValue15 < intValue12; intValue15++) {
            int intValue16 = intValues[intValue15];
            this.resolve(fs[intValue16]);
            this.intValue++;
            this.floatValue *= 0.9F;
            this.floatValue2 *= 0.999F;
            float[] floatValues5 = this.floats3[intValue13];
            float[] floatValues6 = this.floats4[intValue13];
            float[] floatValues7 = gs[intValue16];

            for (int intValue17 = 0; intValue17 < floatValues5.length; intValue17++) {
               floatValues6[intValue17] = floatValues5[intValue17] - floatValues7[intValue17];
            }

            for (int intValue18 = intValue13 - 1; intValue18 >= 1; intValue18--) {
               float[] floatValues8 = this.floats4[intValue18];
               float[] floatValues9 = this.floats4[intValue18 + 1];
               float[][] floatValuesValues2 = this.floats[intValue18];
               float[] floatValues10 = this.floats3[intValue18];

               for (int intValue19 = 0; intValue19 < floatValues8.length; intValue19++) {
                  floatValues8[intValue19] = 0.0F;
               }

               for (int intValue20 = 0; intValue20 < floatValues9.length; intValue20++) {
                  float floatValue3 = floatValues9[intValue20];
                  float[] floatValues11 = floatValuesValues2[intValue20];

                  for (int intValue21 = 0; intValue21 < floatValues8.length; intValue21++) {
                     floatValues8[intValue21] += floatValue3 * floatValues11[intValue21];
                  }
               }

               for (int intValue22 = 0; intValue22 < floatValues8.length; intValue22++) {
                  float floatValue4 = floatValues10[intValue22];
                  floatValues8[intValue22] *= 1.0F - floatValue4 * floatValue4;
               }
            }

            float floatValue5 = 1.0F / (1.0F - this.floatValue);
            float floatValue6 = 1.0F / (1.0F - this.floatValue2);

            for (int intValue23 = 0; intValue23 < intValue13; intValue23++) {
               float[] floatValues12 = this.floats3[intValue23];
               float[] floatValues13 = this.floats4[intValue23 + 1];
               float[][] floatValuesValues3 = this.floats[intValue23];
               float[] floatValues14 = this.floats2[intValue23];
               float[][] floatValuesValues4 = this.floats5[intValue23];
               float[][] floatValuesValues5 = this.floats6[intValue23];
               float[] floatValues15 = this.floats7[intValue23];
               float[] floatValues16 = this.floats8[intValue23];

               for (int intValue24 = 0; intValue24 < floatValues13.length; intValue24++) {
                  float floatValue7 = floatValues13[intValue24];
                  floatValues15[intValue24] = 0.9F * floatValues15[intValue24] + 0.100000024F * floatValue7;
                  floatValues16[intValue24] = 0.999F * floatValues16[intValue24] + 9.999871E-4F * floatValue7 * floatValue7;
                  floatValues14[intValue24] -= f * (floatValues15[intValue24] * floatValue5) / ((float)Math.sqrt(floatValues16[intValue24] * floatValue6) + 1.0E-8F);
                  float[] floatValues17 = floatValuesValues3[intValue24];
                  float[] floatValues18 = floatValuesValues4[intValue24];
                  float[] floatValues19 = floatValuesValues5[intValue24];

                  for (int intValue25 = 0; intValue25 < floatValues12.length; intValue25++) {
                     float floatValue8 = floatValue7 * floatValues12[intValue25];
                     floatValues18[intValue25] = 0.9F * floatValues18[intValue25] + 0.100000024F * floatValue8;
                     floatValues19[intValue25] = 0.999F * floatValues19[intValue25] + 9.999871E-4F * floatValue8 * floatValue8;
                     floatValues17[intValue25] -= f * (floatValues18[intValue25] * floatValue5) / ((float)Math.sqrt(floatValues19[intValue25] * floatValue6) + 1.0E-8F);
                  }
               }
            }
         }
      }
   }

   public float measure(float[][] fs, float[][] gs) {
      this.invoke();
      double doubleValue = 0.0;

      for (int intValue26 = 0; intValue26 < fs.length; intValue26++) {
         float[] floatValues20 = this.resolve(fs[intValue26]);
         float[] floatValues21 = gs[intValue26];

         for (int intValue27 = 0; intValue27 < floatValues20.length; intValue27++) {
            float floatValue9 = floatValues20[intValue27] - floatValues21[intValue27];
            doubleValue += floatValue9 * floatValue9;
         }
      }

      return (float)(doubleValue / Math.max(1, fs.length));
   }

   private void invoke3() {
      this.floats4 = new float[this.ints.length][];

      for (int intValue28 = 0; intValue28 < this.ints.length; intValue28++) {
         this.floats4[intValue28] = new float[this.ints[intValue28]];
      }

      int intValue29 = this.floats.length;
      this.floats5 = new float[intValue29][][];
      this.floats6 = new float[intValue29][][];
      this.floats7 = new float[intValue29][];
      this.floats8 = new float[intValue29][];

      for (int intValue30 = 0; intValue30 < intValue29; intValue30++) {
         int intValue31 = this.ints[intValue30 + 1];
         int intValue32 = this.ints[intValue30];
         this.floats5[intValue30] = new float[intValue31][intValue32];
         this.floats6[intValue30] = new float[intValue31][intValue32];
         this.floats7[intValue30] = new float[intValue31];
         this.floats8[intValue30] = new float[intValue31];
      }

      this.intValue = 0;
      this.floatValue = 1.0F;
      this.floatValue2 = 1.0F;
   }

   private static void invoke4(int[] is) {
      for (int intValue33 = is.length - 1; intValue33 > 0; intValue33--) {
         int intValue34 = ThreadLocalRandom.current().nextInt(intValue33 + 1);
         int intValue35 = is[intValue33];
         is[intValue33] = is[intValue34];
         is[intValue34] = intValue35;
      }
   }

   public boolean check3(Path path) {
      try {
         Files.createDirectories(path.getParent());

         try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(this, bufferedWriter);
         }

         return true;
      } catch (Throwable exception) {
         return false;
      }
   }

   public static AiRotationNeuralNetwork resolve2(Path path) {
      try {
         if (!Files.isRegularFile(path)) {
            return null;
         } else {
            AiRotationNeuralNetwork aiRotationNeuralNetwork;
            try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
               AiRotationNeuralNetwork aiRotationNeuralNetwork2 = (AiRotationNeuralNetwork)GSON.fromJson(bufferedReader, AiRotationNeuralNetwork.class);
               aiRotationNeuralNetwork = aiRotationNeuralNetwork2 != null && aiRotationNeuralNetwork2.check() ? aiRotationNeuralNetwork2 : null;
            }

            return aiRotationNeuralNetwork;
         }
      } catch (Throwable exception2) {
         return null;
      }
   }
}
