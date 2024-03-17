package haxmech.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import haxmech.data.util.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lwjgl.util.vector.Vector2f;

import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicRender;

import static com.fs.starfarer.api.util.Misc.normalizeAngle;
import static haxmech.data.util.AnimConstants.*;
import static haxmech.data.util.BodyUtil.*;

// this class handles arm-specific logic, namely the stategraph involved with swatting vs shooting




public class HaxGModule extends BaseHullMod {

    private static final Logger LOG = Global.getLogger(HaxGModule.class);

    private static final String LEGS_SLOT_NAME = "F_LEGS";
    private static final String WAIST_SLOT_NAME = "E_WAIST";
    private static final String TORSO_SLOT_NAME = "D_TORSO";
    private static final String SHOULDERS_SLOT_NAME = "C_SHOULDERS";
    private static final String HEAD_SLOT_NAME = "B_HEAD";
    private static final String BACKPACK_SLOT_NAME = "A_BACKPACK";

    private boolean runOnce = false;

    private WeaponAPI head;
    private WeaponAPI torso;

    private ShipAPI shoulders_module;
    private WeaponAPI armL;
    private WeaponAPI armR;

    private ShipAPI waist_module;
    private WeaponAPI waist;

    private ShipAPI legs_module;
    private WeaponAPI legs;

    private ShipAPI backpack_module;

    int legTorsoFrame = 0;
    //float laggingLegFacing = 0f;
    Vector2f laggingPos;
    Vector2f laggingVel;

    int armLFrame = 0;
    ArmData armLData = new ArmData();


    int armRFrame = 0;
    ArmData armRData = new ArmData();

    IntervalUtil interval = new IntervalUtil(0.01f, 0.03f);

    void resetLagging(ShipAPI ship) {
        laggingPos = Vector2f.add(
                ship.getLocation(),
                (Vector2f)BodyUtil.normInDirection(ship.getFacing()).scale(-10000) ,
                null
        );
        laggingVel = new Vector2f(0f, 0f);
    }


    public void init(ShipAPI ship) {
        runOnce = true;
        resetLagging(ship);

        if (ship.getCaptain() != null) {
            ship.getCaptain().setPersonality("reckless");
        }

        //need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : ship.getAllWeapons())
        {
            switch (w.getSlot().getId()) {
                case TORSO_SLOT_NAME:
                    if(torso==null) {
                        torso = w;
                    }
                    break;

                case HEAD_SLOT_NAME:
                    if(head==null) {
                        head = w;
                    }
                    break;
            }
        }

        for (ShipAPI m : ship.getChildModulesCopy()) {
            switch (m.getStationSlot().getId()) {
                case SHOULDERS_SLOT_NAME:
                    if(shoulders_module == null) {
                        shoulders_module = m;
                    }
                    break;
                case WAIST_SLOT_NAME:
                    if(waist_module == null) {
                        waist_module = m;
                    }
                    break;
                case LEGS_SLOT_NAME:
                    if(legs_module == null) {
                        legs_module = m;
                    }
                    break;
                case BACKPACK_SLOT_NAME:
                    if(backpack_module == null) {
                        backpack_module = m;
                    }
                    break;
            }
        }

        if (shoulders_module != null) {
            for (WeaponAPI w : shoulders_module.getAllWeapons()) {
                switch (w.getSlot().getId()) {
                    case "ARM_L":
                        if (armL == null) {
                            armL = w;
                        }
                        break;
                    case "ARM_R":
                        if (armR == null) {
                            armR = w;
                        }
                        break;
                }
            }
        }

        if (waist_module != null) {
            for (WeaponAPI w : waist_module.getAllWeapons()) {
                switch (w.getSlot().getId()) {
                    case "WAIST":
                        if(waist==null) {
                            waist = w;
                        }
                        break;
                }
            }
        }

        if (legs_module != null) {
            for (WeaponAPI w : legs_module.getAllWeapons()) {
                switch (w.getSlot().getId()) {
                    case "LEGS":
                        if(legs==null) {
                            legs = w;
                        }
                        break;
                }
            }
        }

        // this unit is biological and doesn't take dumb systems damage
        ship.getMutableStats().getEngineDamageTakenMult().setBaseValue(0f);
        ship.getMutableStats().getWeaponDamageTakenMult().setBaseValue(0f);

        shoulders_module.getMutableStats().getWeaponDamageTakenMult().setBaseValue(0f);
        legs_module.getMutableStats().getWeaponDamageTakenMult().setBaseValue(0f);
        waist_module.getMutableStats().getWeaponDamageTakenMult().setBaseValue(0f);
        //ogColor = new Color(ship.getSprite().getColor().getRed()/255f,ship.getSprite().getColor().getGreen()/255f,ship.getSprite().getColor().getBlue()/255f);
    }

    Vector2f legLagDelta() {
        return Vector2f.sub(
                laggingPos,
                legs.getLocation(),
                null);
    }

    // updates the inertial spring portion of the leg lag/spring system. Angle clamping is performed elsewhere.
    void updateLegLag(ShipAPI ship, float elapsed) {
        if (ship.getMaxSpeed() != 0f) {
            Vector2f lagUpdateDelta = legLagDelta();

            laggingPos = Vector2f.add(
                    laggingPos,
                    BodyUtil.scaleVecCopy(
                            laggingVel,
                            elapsed
                    ),
                    null);
            dampLegVel(ship, elapsed);

            // ensure the lag position can never get farther away than a fraction of
            // the leg length determined by our speed
            {
                float legLimit = AutogenFrameData.LEG_LENGTH * BodyUtil.clamp(
                        ship.getVelocity().length() / ship.getMaxSpeed(),
                        // we always allow the legs movement within a small radius, to sell the illusion
                        // of inertia
                        300f / AutogenFrameData.LEG_LENGTH, 1f);

                Vector2f lagDelta = legLagDelta();
                float lagAmount = lagDelta.length();

                if (lagAmount > legLimit) {
                    Vector2f movedAmount = scaleVecCopy(
                            lagDelta, legLimit / lagAmount
                    );

                    // the amount we moved gets added to velocity, so that
                    // the "leg stopping inertia" continues
                    laggingVel = Vector2f.add(
                            laggingVel,
                            (Vector2f) Vector2f.sub(
                                    movedAmount,
                                    lagDelta,
                                    null
                            ).scale(1f/elapsed),
                            null
                    );

                    laggingPos = Vector2f.add(
                            legs.getLocation(),
                            movedAmount,
                            null
                    );
                }
            }
        } else {
            resetLagging(ship);
        }
    }

    void dampLegVel(ShipAPI ship, float elapsed) {
        Vector2f lagUpdateDelta = legLagDelta();
        float ludLength = lagUpdateDelta.length();

        // lag velocity damps to % of ship velocity rather than 0.
        // This means that the legs will always eventually extend, but
        // will behave inertially much of the time.
        // We also damp harder the farther away we are.
        float velDampFactor = clamp(
                (1f - ludLength / AutogenFrameData.LEG_LENGTH),
                0.1f, 1f);
        Vector2f dampTargetVel = scaleVecCopy(ship.getVelocity(),0.3f);

        if (velDampFactor != 0f) {
            laggingVel = scaleVecToOffsetCopy(
                    dampTargetVel,
                    laggingVel,
                    (float) Math.pow(
                            1f - LEGS_VEL_DAMP_CONSTANT,
                            elapsed / velDampFactor)
            );
        } else {
            laggingVel = dampTargetVel;
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if(ship == null || !Global.getCombatEngine().isEntityInPlay(ship))
            return;

        //if(!MagicRender.screenCheck(0.5f, weapon.getLocation()) || Global.getCombatEngine().isPaused())
        //{
        //    return;
        //}

        if(!ship.isAlive())
            return;


        if(ship.getOwner() == -1)
        {
            torso.getAnimation().setFrame(MID_BODY_FRAME);
            legs.getAnimation().setFrame(MID_BODY_FRAME);
            return;
        }

        if(!runOnce)
            init(ship);
        if(head == null || torso == null || legs == null || shoulders_module == null
                || armL == null || armR == null || legs_module == null || waist_module == null || waist == null)
            return;


        assert !Float.isNaN(laggingPos.x);
        assert !Float.isNaN(laggingPos.y);
        assert !Float.isNaN(laggingVel.x);
        assert !Float.isNaN(laggingVel.y);

        // undriveable. If the player is controlling this, shunt them to the backpack instead.
        if (ship == Global.getCombatEngine().getPlayerShip()) {
            Global.getCombatEngine().setPlayerShipExternal(backpack_module);
        }

        head.setForceNoFireOneFrame(true);

        interval.advance(amount);
        float elapsed = interval.getElapsed();

        updateLegLag(ship, elapsed);

        // lagDelta points from the ship to the lag point
        Vector2f lagDelta = legLagDelta();
        float lagDeltaLength = lagDelta.length();

        Vector2f normShipFacing = BodyUtil.normInDirection(ship.getFacing());
        float frontBackLagDelta = -Vector2f.dot(lagDelta, normShipFacing);


        LOG.info("LP1 " + laggingPos);

        //// lagDelta is from the ship towards the lag position, and the legs face in the opposite direction, hence negation
        // legs face forwards if we're moving forward, and backwards if we're moving backwards
        // (because we handle the flip by drawing a reversed frame)
        float laggingLegFacing = (frontBackLagDelta > 0)
                ? (float) (FastTrig.atan2(-lagDelta.y, -lagDelta.x) * 180f / Math.PI)
                : (frontBackLagDelta < 0)
                ? (float) (FastTrig.atan2(lagDelta.y, lagDelta.x) * 180f / Math.PI)
                : ship.getFacing();

        float origLaggingLegFacing = laggingLegFacing;

        float velFacing = BodyUtil.angleForVec(ship.getVelocity());
        if (Float.isNaN(velFacing)) {
            velFacing = ship.getFacing();
        }
        if (frontBackLagDelta <= 0) {
            velFacing = normalizeAngle(180f + velFacing);
        }
        float velFacingDelta = angleDelta(velFacing, laggingLegFacing);

        laggingLegFacing += 1f * elapsed * velFacingDelta;


        float facingDelta = angleDelta(ship.getFacing(), laggingLegFacing);

        // we constrain the angle less depending on how far away we are, and we
        // constrain it much more if we're close.
        // the goal is for the angle to always "straighten out" by the time the
        // legs are directly under the body
        float legsMaxLagAngle = LEGS_MAX_LAG_ANGLE * (0.05f +
                0.95f *
                BodyUtil.clamp(
                        lagDeltaLength / AutogenFrameData.LEG_LENGTH, 0f, 1f)
                );

        if (facingDelta > legsMaxLagAngle) {
            laggingLegFacing = ship.getFacing() - legsMaxLagAngle;
            facingDelta = legsMaxLagAngle;
        } else if (facingDelta < -legsMaxLagAngle) {
            laggingLegFacing = ship.getFacing() + legsMaxLagAngle;
            facingDelta = -legsMaxLagAngle;
        }

        laggingPos = Vector2f.add(
                legs_module.getLocation(),
                 BodyUtil.rotateVec(lagDelta, laggingLegFacing - origLaggingLegFacing),
                null
        );

        // calculate location of shoulder module, which keeps the arms in sync with the shoulders
        Vector2f newShouldersLocation = ship.getHullSpec().getWeaponSlotAPI(SHOULDERS_SLOT_NAME).computePosition(ship);
        newShouldersLocation = Vector2f.add(
                newShouldersLocation,
                (Vector2f) BodyUtil.normInDirection(ship.getFacing()).scale(AutogenFrameData.FRAME_SHOULDER_OFFSETS[legTorsoFrame]),
                null
        );
        shoulders_module.getLocation().set(newShouldersLocation);
        shoulders_module.getStationSlot().setAngle(0);

        backpack_module.getLocation().set(
                Vector2f.add(newShouldersLocation,
                (Vector2f) BodyUtil.normInDirection(ship.getFacing()).scale(-100),
                null));
        backpack_module.getStationSlot().setAngle(180);

        // calculate location of legs module, which keeps the legs in sync with the torso
        Vector2f newLegsLocation = ship.getHullSpec().getWeaponSlotAPI(LEGS_SLOT_NAME).computePosition(ship);
        newLegsLocation = Vector2f.add(
                newLegsLocation,
                (Vector2f) BodyUtil.normInDirection(ship.getFacing()).scale(AutogenFrameData.FRAME_PELVIS_OFFSETS[legTorsoFrame]),
                null
        );
        legs_module.getLocation().set(newLegsLocation);
        legs_module.setFacing(laggingLegFacing); //.getStationSlot().setAngle(facingDelta);

        // calculate location of waist module, which is a midsegment to allow the body to bend slightly more
        Vector2f newWaistLocation = ship.getHullSpec().getWeaponSlotAPI(WAIST_SLOT_NAME).computePosition(ship);
        newWaistLocation = Vector2f.add(
                newWaistLocation,
                (Vector2f) BodyUtil.normInDirection(ship.getFacing()).scale(AutogenFrameData.FRAME_SPINE_OFFSETS[legTorsoFrame]),
                null
        );
        waist_module.getLocation().set(newWaistLocation);
        waist_module.setFacing(laggingLegFacing + 0.7f * angleDelta(ship.getFacing(), laggingLegFacing));

        // calculate leg/torso frame based on foot position
        // legTorsoFrame = BodyUtil.frameForFootOffset(-frontBackLagDelta);
        // calculate body frames based on lagging position offset. This is roughly correlated with velocity.
        // this is a value on [0, 1]
        float frameCoef = 1f - ((frontBackLagDelta / (AutogenFrameData.LEG_LENGTH * 2f)) + 0.5f);
        if (frameCoef > 1f) frameCoef = 1f;
        if (frameCoef < 0f) frameCoef = 0f;
        // 0 is full forwards, 1 full backwards
        legTorsoFrame = Math.round(
            frameCoef * (float) (N_BODY_FRAMES - 1)
        );

        // each arm can only swat if the other one isn't, since the double-swat looks dumb
        armLFrame = armLData.handleArmAndReturnFrame(
                ship, armL, elapsed, ARM_L_SWAT_START, ARM_L_SWAT_END,
                false, armRData.state() == ArmState.NORMAL, true);
        armRFrame = armRData.handleArmAndReturnFrame(
                ship, armR, elapsed, ARM_R_SWAT_START, ARM_R_SWAT_END,
                true, armLData.state() == ArmState.NORMAL, false);

        // render order is bottom to top (since it's painter's algorithm)
        BodyUtil.drawOneFrame(legs_module, legs, "girllegs", legTorsoFrame);
        BodyUtil.drawOneFrame(waist_module, waist, "girlwaist", legTorsoFrame);
        BodyUtil.drawOneFrame(shoulders_module, armL, "girlarm_l", armLFrame);
        BodyUtil.drawOneFrame(shoulders_module, armR, "girlarm_r", armRFrame);
        BodyUtil.drawOneFrame(ship, torso, "girltorso", legTorsoFrame);

        //drawDebugDot(laggingPos);
    }
}

