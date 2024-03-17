from typing import TYPE_CHECKING
import json5

from config import IMG_CENTER, BODY_FRAMES, \
    ARMS_FRAMES, ARMS_SWAT_FRAMES, ARM_JOINT_FUDGE_X, \
    ARM_JOINT_FUDGE_Y
from img_util import AABB
if TYPE_CHECKING:
    from main import JointOffsets, AllTrackers

PREFIX: str = "../data"

def dump_wpn(wpn_dict):
    with open(f"{PREFIX}/weapons/{wpn_dict["id"]}.wpn", 'w') as wf:
        json5.dump(wpn_dict, wf, indent=2)

def dump_hull(ship_dict):
    with open(f"{PREFIX}/hulls/{ship_dict["hullId"]}.ship", 'w') as wf:
        json5.dump(ship_dict, wf, indent=2)

def dump_variant(variant_dict):
    with open(f"{PREFIX}/variants/{variant_dict["variantId"]}.variant", 'w') as wf:
        json5.dump(variant_dict, wf, indent=2)

def gen_hull_json(lt_offsets: "JointOffsets", all_trackers: "AllTrackers"):
    with open("./templates/ship.ship", 'r') as rf:
        ship_dict = json5.load(rf)
        # shoulders
        ship_dict["weaponSlots"][2]["locations"][0] = IMG_CENTER - (lt_offsets.shoulder_base_offset + ARM_JOINT_FUDGE_Y)
        # waist
        ship_dict["weaponSlots"][4]["locations"][0] = IMG_CENTER - lt_offsets.spine_base_offset
        # legs
        ship_dict["weaponSlots"][5]["locations"][0] = IMG_CENTER - lt_offsets.pelvis_base_offset
        # backpack
        ship_dict["weaponSlots"][0]["locations"][0] = IMG_CENTER - (
            lt_offsets.shoulder_base_offset + 100)
        dump_hull(ship_dict)
    with open("./templates/shouldermodule.ship", 'r') as rf:
        canonical_shoulder_tracker = all_trackers.shouldertrackers[BODY_FRAMES[0]]
        x_off = canonical_shoulder_tracker.x - ARM_JOINT_FUDGE_X
        ship_dict = json5.load(rf)
        ship_dict["weaponSlots"][0]["locations"][0] = 0 #( IMG_CENTER - canonical_shoulder_tracker.y)
        ship_dict["weaponSlots"][0]["locations"][1] = (
            IMG_CENTER - x_off
        )

        ship_dict["weaponSlots"][1]["locations"][0] = 0 #( IMG_CENTER - canonical_shoulder_tracker.y)
        ship_dict["weaponSlots"][1]["locations"][1] = (
            x_off - IMG_CENTER
        )
        dump_hull(ship_dict)
    with open("./templates/legsmodule.ship", 'r') as rf:
        ship_dict = json5.load(rf)
        dump_hull(ship_dict)
    with open("./templates/waistmodule.ship", 'r') as rf:
        ship_dict = json5.load(rf)
        dump_hull(ship_dict)

def gen_variant_json():
    with open("./templates/ship_standard.variant", 'r') as rf:
        variant_dict = json5.load(rf)
        dump_variant(variant_dict)
    with open("./templates/shouldermodule_standard.variant", 'r') as rf:
        variant_dict = json5.load(rf)
        dump_variant(variant_dict)
    with open("./templates/legsmodule_standard.variant", 'r') as rf:
        variant_dict = json5.load(rf)
        dump_variant(variant_dict)
    with open("./templates/waistmodule_standard.variant", 'r') as rf:
        variant_dict = json5.load(rf)
        dump_variant(variant_dict)

def gen_wpn_json(arms_aabb: AABB):
    arm_length = round((arms_aabb.y1 - arms_aabb.y0) * 0.9)
    with open("./templates/arml.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        wpn_dict["turretOffsets"][0] = arm_length
        wpn_dict["numFrames"] = (
            max(ARMS_FRAMES[1], ARMS_SWAT_FRAMES[1]) -
            min(ARMS_FRAMES[0], ARMS_SWAT_FRAMES[0]) + 1
        )
        dump_wpn(wpn_dict)
    with open("./templates/armr.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        wpn_dict["turretOffsets"][0] = arm_length
        wpn_dict["numFrames"] = (
            max(ARMS_FRAMES[1], ARMS_SWAT_FRAMES[1]) -
            min(ARMS_FRAMES[0], ARMS_SWAT_FRAMES[0]) + 1
        )
        dump_wpn(wpn_dict)
    with open("./templates/head.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        dump_wpn(wpn_dict)

    with open("./templates/torso.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        wpn_dict["numFrames"] = (
            BODY_FRAMES[1] - BODY_FRAMES[0] + 1
        )
        dump_wpn(wpn_dict)
    with open("./templates/waist.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        wpn_dict["numFrames"] = (
            BODY_FRAMES[1] - BODY_FRAMES[0] + 1
        )
        dump_wpn(wpn_dict)
    with open("./templates/legs.wpn", 'r') as rf:
        wpn_dict = json5.load(rf)
        wpn_dict["numFrames"] = (
            BODY_FRAMES[1] - BODY_FRAMES[0] + 1
        )
        dump_wpn(wpn_dict)