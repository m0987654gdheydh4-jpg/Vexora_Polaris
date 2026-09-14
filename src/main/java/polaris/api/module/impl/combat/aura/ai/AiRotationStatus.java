package polaris.api.module.impl.combat.aura.ai;

public record AiRotationStatus(String text, boolean training, boolean loadingModel, long queuedRecords, long writtenRecords, long droppedRecords, long updatedAtMs) {
   public static AiRotationStatus idle() {
      return new AiRotationStatus("AI idle", false, false, 0L, 0L, 0L, System.currentTimeMillis());
   }
}
