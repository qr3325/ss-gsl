package haxmech.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class BodyUtil {
    private static final Logger LOG = Global.getLogger(BodyUtil.class);

    public static float angleForVec(Vector2f vec) {
        return (float) (FastTrig.atan2(vec.y, vec.x) * 180f / Math.PI);
    }

    // rotates a vec by angle. Returns the rotated vec. Positive angle is CCW.
    public static Vector2f rotateVec(Vector2f vec, float angle) {
        // 2d rotation matrix is
        // [cos -sin] [x]
        // [sin cos] [y]

        float angleRads = (float) (angle * Math.PI / 180f);
        float cos = (float)FastTrig.cos(angleRads);
        float sin = (float)FastTrig.sin(angleRads);

        return new Vector2f(
            cos*vec.x + -sin*vec.y,
            sin*vec.x + cos*vec.y
        );
    }

    public static Vector2f clampMagnitudeCopy(Vector2f vec, float max) {
        Vector2f newVec = new Vector2f(vec.x, vec.y);
        float lenSq = newVec.lengthSquared();
        if (lenSq > max * max) {
            newVec.scale(max / (float)Math.sqrt(lenSq));
        }
        return newVec;
    }

    // nondestructive vector scaling. Returns a new vector.
    public static Vector2f scaleVecCopy(Vector2f vec, float scale) {
        Vector2f newVec = new Vector2f(vec.x, vec.y);
        newVec.scale(scale);
        return newVec;
    }

    // As scaleVec, however, reduces the new vector "towards" origin rather than (0,0).
    public static void scaleVecToOffset(Vector2f vec, Vector2f origin, float scale) {
        Vector2f.sub(vec, origin, vec);
        vec.scale(scale);
        Vector2f.add(vec, origin, vec);
    }

    public static Vector2f scaleVecToOffsetCopy(Vector2f vec, Vector2f origin, float scale) {
        Vector2f newVec = new Vector2f(vec.x, vec.y);
        scaleVecToOffset(newVec, origin, scale);
        return newVec;
    }

    public static Vector2f normInDirection(float angle) {
        // this kinda sucks because math.sin/cos is double so we have to cast it which is a copy + wasted work blah blah
        double rads = angle * Math.PI / 180f;
        return new Vector2f((float) FastTrig.cos(rads), (float) FastTrig.sin(rads));
    }

    public static float magnitudeInDirection(Vector2f d, float angle) {
        Vector2f normalD = normInDirection(angle);
        return Vector2f.dot(d, normalD);
    }

    public static float absToSrAngle(ShipAPI ship, float angle) {
        float ret = angle - ship.getFacing();
        if (ret < 0) ret += 360f;
        return ret;
    }

    public static float srToAbsAngle(ShipAPI ship, float angle) {
        float ret = angle + ship.getFacing();
        if (ret >= 360f) ret -= 360f;
        return ret;
    }

    // calculates a - b, but returns the shortest angular offset between them (e.g. respects modulo 360).
    // Always returns a value on [-180, 180].
    public static float angleDelta(float a, float b)  {
        if (a - b > 180f) {
            return angleDelta(a - 360f, b);
        } else if (a - b <= -180f) {
            return angleDelta(a + 360f, b);
        } else {
            return a - b;
        }
    }

    public static float smoothToRange(float x, float fromMin, float fromMax, float toMin, float toMax) {
        if (x <= fromMin) {
            return toMin;
        }
        if (x >= fromMax) {
            return toMax;
        }
        float magicNumber = -(toMax - toMin) / 2;
        return (float) (FastTrig.cos((x - fromMin) * (1 / (fromMax - fromMin)) * MathUtils.FPI)) * magicNumber - magicNumber + toMin;
    }

    public static float clamp(float in, float min, float max) {
        if (in > max) return max;
        if (in < min) return min;
        return in;
    }

    public static void drawDebugDot(Vector2f pos, Color c) {
        SpriteAPI spr = Global.getSettings().getSprite(
            "graphics/haxmech/ships/g1_backpack/plus_pestilence.png"
        );

        MagicRender.singleframe(
                spr,
                pos,
                new Vector2f(spr.getWidth(), spr.getHeight()),
                0,
                c,
                false,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );

        LOG.info("DEBUG DOT AT " + pos);
    }


    public static void drawOneFrame(ShipAPI ship, WeaponAPI weapon, String name, int frame) {
        weapon.getAnimation().setFrame(frame);

        String spriteName = (frame < 10)
                ? "graphics/haxmech/ships/g1/" + name + "0"+frame+".png"
                : "graphics/haxmech/ships/g1/" + name + frame+".png";
        SpriteAPI spr = Global.getSettings().getSprite(spriteName);
        //Color defColor = weapon.getSprite().getAverageColor();

        //Color color = new Color(defColor.getRed()+60,defColor.getGreen()+60,defColor.getBlue()+60);
        //color = new Color(color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,(color.getAlpha()/255f)*ship.getCombinedAlphaMult());

        MagicRender.singleframe(
                spr,
                new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()),
                new Vector2f(spr.getWidth(),spr.getHeight()),
                weapon.getCurrAngle() - 90f,
                Color.WHITE,
                //Color.GRAY,
                false,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );

        weapon.getSprite().setColor(new Color(0f,0f,0f,0f));
    }

    public static Vector2f swatCenter(ShipAPI ship, boolean leftSide) {
        Vector2f shipLocation = ship.getLocation();
        Vector2f shipFrontLocation = Vector2f.add(
                shipLocation,
                (Vector2f) normInDirection(ship.getFacing()).scale(300f) ,
                null);

        if (leftSide) {
            return Vector2f.add(
                    shipFrontLocation,
                    (Vector2f) normInDirection(ship.getFacing() + 90).scale(350f),
                    null
                    );
        } else {
            return Vector2f.add(
                    shipFrontLocation,
                    (Vector2f) normInDirection(ship.getFacing() - 90).scale(300f),
                    null
                    );
        }
    }


    public static boolean hasSwattableStuff(ShipAPI ship, boolean leftSide) {
        Vector2f swatLocation = swatCenter(ship, leftSide);

        //drawDebugDot(swatLocation, leftSide ? Color.RED : Color.GREEN);

        // if there's enough stuff in front of us, swat it. We only swat fighters and frigates.
        float swatScore = 0;
        //for (ShipAPI tShip : CombatUtils.getShipsWithinRange(swatLocation, 450f)) {
        for (CombatEntityAPI target: CombatUtils.getEntitiesWithinRange(swatLocation, 450f)) {
            if (target.getOwner() == ship.getOwner()) { continue; }

            float scoreToAdd = 0f;

            if (target instanceof ShipAPI) {
                ShipAPI tShip = (ShipAPI) target;
                // we swat ships up to destroyer-sized
                if (tShip.getHullSize() == ShipAPI.HullSize.FIGHTER) {
                    scoreToAdd += 1.0f;
                } else if (tShip.getHullSize() == ShipAPI.HullSize.FRIGATE) {
                    scoreToAdd += 3.0f;
                } else if (tShip.getHullSize() == ShipAPI.HullSize.DESTROYER) {
                    scoreToAdd += 10.0f;
                }

                // wrecks contribute less generally, and have a cap to their contribution
                if (!tShip.isAlive()) {
                    scoreToAdd /= 2;
                    scoreToAdd = Math.max(scoreToAdd, 2f);
                }
            } else if (target instanceof MissileAPI) {
                scoreToAdd += 0.5f;
            }

            swatScore += scoreToAdd;
        }
        return swatScore > 1f + (Math.random() * 10f);
    }

    public static void doSwatEffect(ShipAPI ship, boolean leftSide, boolean shoveLeft) {
        Vector2f swatLocation = swatCenter(ship, leftSide);

        float swatForceMinAngle = (shoveLeft) ? ship.getFacing() + 90f : ship.getFacing() - 90f;
        float swatForceMaxAngle = (shoveLeft) ? ship.getFacing() + 60f : ship.getFacing() - 60f;

        for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(swatLocation, 500f)) {
            if (target.getOwner() == ship.getOwner()) continue;
            // applyForce multiples force by 100; hence, this will randomly
            // send the swatted objects flying at 1000-1500 speed
            CombatUtils.applyForce(
                    target,
                    swatForceMinAngle + (swatForceMaxAngle - swatForceMinAngle) * (float)Math.random(),
                    target.getMass() * (
                            10f + 5f*(float)Math.random())
            );
            // we also set a random spin on ships
            if (target.getMass() < 200f) {
                target.setAngularVelocity(360f * 6f * ((float) Math.random() - 0.5f));
            } else {
                target.setAngularVelocity(
                        360f * 6f * 200f / target.getMass() * ((float) Math.random() - 0.5f));
            }

            // disable engines depending on target size
            if (target instanceof ShipAPI) {
                ShipAPI targetShip = (ShipAPI) target;

                // smaller frigates, like hound/wolf have 200 mass. Hence, anything
                // hound-sized or smaller is guaranteed flameout, but destroyers/cruisers will almost never.
                if (targetShip.getMass() * (float)Math.random() < 200f) {
                    targetShip.getEngineController().forceFlameout();
                }
            } else if (target instanceof MissileAPI) {
                MissileAPI targetMissile = (MissileAPI) target;
                targetMissile.getEngineController().forceFlameout();
            }

            // we want to 1-2 hit kill everything we swat.
            Global.getCombatEngine().applyDamage(
                    target, target.getLocation(),
                    Math.min(2000f, 2f * (float)Math.random() * target.getMaxHitpoints()),
                    DamageType.FRAGMENTATION, 0, false, false, null
            );

        }
    }

    // the offset is foot - pelvis, so a negative value means the foot is below the pelvis
    // (assuming forward facing is up)
    public static int frameForFootOffset(float offset) {
        for (int i = 0; i < AutogenFrameData.FRAME_FOOT_OFFSETS.length; i++) {
            if (AutogenFrameData.FRAME_FOOT_OFFSETS[i] > offset) {
                if (i > 0) {
                    if (
                        (AutogenFrameData.FRAME_FOOT_OFFSETS[i] - offset) <
                        (offset - AutogenFrameData.FRAME_FOOT_OFFSETS[i-1])
                    ) {
                        return i;
                    } else {
                        return i-1;
                    }
                }
                return i;
            }
        }
        return AutogenFrameData.FRAME_FOOT_OFFSETS.length - 1;
    }
}
