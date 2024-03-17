package haxmech.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.apache.log4j.Logger;

import static haxmech.data.util.BodyUtil.angleDelta;

public class ArmData {
    private static final Logger LOG = Global.getLogger(ArmData.class);

    private static final float SWAT_CHECK_COOLDOWN = 1f;
    ArmState state = ArmState.NORMAL;
    int frame = 0;
    float swatProgress = 0f;


    float lastCheckedSwat = 0f;

    public ArmState state() {
        return state;
    }

    void doSwatSetup(
            ShipAPI ship,
            WeaponAPI arm,
            float elapsed,
            float swatStart,
            float swatEnd,
            float swatDir,
            ArmState next,
            boolean reverseFrames
    ) {
        swatProgress += elapsed;

        arm.setSuspendAutomaticTurning(true);
        arm.setForceNoFireOneFrame(true);
        if (-swatDir * angleDelta(arm.getCurrAngle(), swatStart + ship.getFacing() - 90f) <= 0) {
            arm.setCurrAngle(arm.getCurrAngle() + -swatDir * 45f * elapsed);
        }

        frame = Math.round(BodyUtil.smoothToRange(
                reverseFrames ? (1 - swatProgress) : swatProgress,
                0, 1f,
                AnimConstants.ARM_SWAT_FRAME_OFFSET, AnimConstants.ARM_SWAT_FRAME_OFFSET + AnimConstants.ARM_SWAT_FRAME_SETUP_END));

        if (swatProgress >= 1f) {
            state = next;
            swatProgress = 0f;
        }
    }

    void doSwat(
            ShipAPI ship,
            WeaponAPI arm,
            float elapsed,
            float swatStart,
            float swatEnd,
            float swatDir,
            ArmState next,
            boolean reverseFrames,
            boolean leftSide
    ) {
        swatProgress += 2f * elapsed;

        arm.setSuspendAutomaticTurning(true);
        arm.setForceNoFireOneFrame(true);
        arm.setCurrAngle(BodyUtil.smoothToRange(
                swatProgress,
                0f, 1f,
                swatStart + ship.getFacing() - 90f,
                swatEnd + ship.getFacing() - 90f
        ));

        frame = Math.round(BodyUtil.smoothToRange(
                reverseFrames ? (1 - swatProgress) : swatProgress,
                0, 1f,
                AnimConstants.ARM_SWAT_FRAME_OFFSET + AnimConstants.ARM_SWAT_FRAME_SETUP_END,
                AnimConstants.ARM_SWAT_FRAME_OFFSET + AnimConstants.ARM_SWAT_FRAME_SWAT_END));

        if (swatProgress >= 1f) {
            BodyUtil.doSwatEffect(
                    ship,
                    leftSide,
                    angleDelta(swatEnd, swatStart) >= 0
                    );
            //if (armL.getArcFacing() <= ARM_L_SWAT_END + 1f) {
            state = next;
            swatProgress = 0f;
        }
    }

    void doSwatCooldown(
            ShipAPI ship,
            WeaponAPI arm,
            float elapsed,
            float swatStart,
            float swatEnd,
            float swatDir,
            ArmState next,
            boolean reverseFrames
    ) {
        swatProgress += elapsed;

        arm.setSuspendAutomaticTurning(true);
        arm.setForceNoFireOneFrame(true);
        frame = Math.round(BodyUtil.smoothToRange(
                reverseFrames ? (1 - swatProgress) : swatProgress,
                0, 1f,
                AnimConstants.ARM_SWAT_FRAME_OFFSET + AnimConstants.ARM_SWAT_FRAME_SWAT_END,
                AnimConstants.ARM_SWAT_FRAME_OFFSET + AnimConstants.N_ARM_SWAT_FRAMES - 1));

        if (-swatDir * angleDelta(arm.getCurrAngle(), swatStart + ship.getFacing() - 90f) <= 0) {
            arm.setCurrAngle(arm.getCurrAngle() + -swatDir * 45f * elapsed);
        }

        if (swatProgress >= 1f) {
            frame = 0;
            state = next;
            swatProgress = 0f;
        }
    }

    // swatStart and swatEnd are for a forehand swat. backhand swat swats from swatEnd to swatStart.
    public int handleArmAndReturnFrame(
            ShipAPI ship, WeaponAPI arm,
            float elapsed, float swatStart, float swatEnd,
            boolean canShoot, boolean canSwat,
            boolean leftSide
    ) {
        float swatDir = (swatEnd - swatStart) / Math.abs(swatEnd - swatStart);

        if (state == ArmState.NORMAL) {
            arm.setSuspendAutomaticTurning(false);
            arm.setForceNoFireOneFrame(!canShoot);

            // calculate weapon animations
            frame = Math.round(
                    BodyUtil.smoothToRange(
                            arm.getChargeLevel() * 3.2f,
                            0f, 1f,
                            AnimConstants.ARM_FIRE_FRAME_OFFSET, AnimConstants.N_ARM_FIRE_FRAMES - 1
                    )
            );
            if (frame >= AnimConstants.N_ARM_FIRE_FRAMES) frame = AnimConstants.N_ARM_FIRE_FRAMES - 1;

            float timeSinceLastSwatCheck = Global.getCombatEngine().getTotalElapsedTime(false) - lastCheckedSwat;

            if (
                canSwat
                && timeSinceLastSwatCheck > SWAT_CHECK_COOLDOWN
                && arm.getChargeLevel() <= 0.1f
                && !arm.isFiring()
                && BodyUtil.hasSwattableStuff(ship, leftSide)) {
                frame = AnimConstants.ARM_SWAT_FRAME_OFFSET;
                swatProgress = 0f;
                lastCheckedSwat = Global.getCombatEngine().getTotalElapsedTime(false);

                if (
                        -swatDir * angleDelta(
                                arm.getCurrAngle(),
                                (swatStart + swatEnd) / 2 + ship.getFacing() - 90f
                        ) <= 0
                ) {
                    state = ArmState.FORESWAT_SETUP;
                } else {
                    state = ArmState.BACKSWAT_SETUP;
                }
            }
        } else if (state == ArmState.FORESWAT_SETUP) {
            doSwatSetup(ship, arm, elapsed, swatStart, swatEnd, swatDir, ArmState.FORESWAT, false);
        } else if (state == ArmState.FORESWAT) {
            doSwat(ship, arm, elapsed, swatStart, swatEnd, swatDir, ArmState.FORESWAT_DONE, false, leftSide);
        } else if (state == ArmState.FORESWAT_DONE) {
            // end in a position that would cause us to immediately do a backswat if we chain swats
            doSwatCooldown(ship, arm, elapsed, (swatStart + swatEnd*2f) / 3f, swatEnd, swatDir, ArmState.NORMAL, false);
        } else if (state == ArmState.BACKSWAT_SETUP) {
            doSwatCooldown(ship, arm, elapsed, swatEnd, swatStart, -swatDir, ArmState.BACKSWAT, true);
        } else if (state == ArmState.BACKSWAT) {
            doSwat(ship, arm, elapsed, swatEnd, swatStart, -swatDir, ArmState.BACKSWAT_DONE, true, leftSide);
        } else if (state == ArmState.BACKSWAT_DONE) {
            // end in a position that would cause us to immediately do a foreswat if we chain swats
            doSwatSetup(ship, arm, elapsed, (swatStart*2f + swatEnd) / 3f, swatStart, -swatDir, ArmState.NORMAL, true);
        }

        return frame;
    }
}
