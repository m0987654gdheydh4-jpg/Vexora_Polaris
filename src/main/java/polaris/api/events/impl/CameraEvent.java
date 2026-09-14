package polaris.api.events.impl;

import polaris.api.events.CancellableEvent;
import polaris.api.module.impl.combat.aura.Angle;

public final class CameraEvent extends CancellableEvent {
    private boolean cameraClip;
    private float distance;
    private float reverseAmount;
    private Angle angle;

    public CameraEvent(boolean cameraClip, float distance, Angle angle) {
        this.cameraClip = cameraClip;
        this.distance = distance;
        this.angle = angle;
        this.reverseAmount = 0.0f;
    }

    public boolean isCameraClip() {
        return cameraClip;
    }

    public void setCameraClip(boolean cameraClip) {
        this.cameraClip = cameraClip;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    
    public float getReverseAmount() {
        return reverseAmount;
    }

    public void setReverseAmount(float reverseAmount) {
        this.reverseAmount = reverseAmount;
    }

    public Angle getAngle() {
        return angle;
    }

    public void setAngle(Angle angle) {
        this.angle = angle;
    }
}

