package polaris.api.module.impl.combat.aura.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AiRotationDataset {
   private static final Gson GSON = new GsonBuilder().create();
   public int intValue;
   public int intValue2;
   public int intValue3;
   public float floatValue;
   public float floatValue2;
   public float floatValue3;
   public float floatValue4;
   public float floatValue5;
   public AiRotationNeuralNetwork aiRotationNeuralNetwork;
   public float[][] floats;
   public float[][] floats2;

   public AiRotationDataset() {
   }

   public AiRotationDataset(int i, int j, int k, AiRotationNeuralNetwork aiRotationNeuralNetwork, float[][] fs, float[][] gs) {
      this.intValue = i;
      this.intValue2 = j;
      this.intValue3 = k;
      this.aiRotationNeuralNetwork = aiRotationNeuralNetwork;
      this.floats = fs;
      this.floats2 = gs;
   }

   public boolean check(int i, int j) {
      return this.aiRotationNeuralNetwork != null
         && this.aiRotationNeuralNetwork.check2(i, j)
         && this.intValue == i
         && this.intValue2 == j
         && this.floats != null
         && this.floats2 != null;
   }

   public float measure(int i, int j) {
      return measure3(this.floats, i, j);
   }

   public float measure2(int i, int j) {
      return measure3(this.floats2, i, j);
   }

   public int compute(int i) {
      return this.floats != null && i >= 0 && i < this.floats.length && this.floats[i] != null ? this.floats[i].length : 0;
   }

   private static float measure3(float[][] fs, int i, int j) {
      if (fs != null && i >= 0 && i < fs.length) {
         float[] floatValues = fs[i];
         return floatValues != null && floatValues.length != 0 ? floatValues[Math.floorMod(j, floatValues.length)] : 0.0F;
      } else {
         return 0.0F;
      }
   }

   public boolean check2(Path path) {
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

   public static AiRotationDataset resolve(Path path) {
      try {
         if (!Files.isRegularFile(path)) {
            return null;
         } else {
            AiRotationDataset aiRotationDataset;
            try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
               aiRotationDataset = (AiRotationDataset)GSON.fromJson(bufferedReader, AiRotationDataset.class);
            }

            return aiRotationDataset;
         }
      } catch (Throwable exception2) {
         return null;
      }
   }
}
