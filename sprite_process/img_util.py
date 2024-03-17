from typing import NamedTuple

from PIL import Image

import numpy as np

class Point2(NamedTuple):
    x: int
    y: int

    def round_from_float_tuple(t: tuple[float,float]) -> "Point2":
        return Point2(round(t[0]), round(t[1]))

    def from_tuple(t: tuple[int,int]) -> "Point2":
        return Point2(t[0], t[1])

class AABB(NamedTuple):
    x0: int
    y0: int
    x1: int
    y1: int

    @staticmethod
    def from_4tuple(t: tuple[int,int,int,int]) -> "AABB":
        return AABB(t[0], t[1], t[2], t[3])

def aabb_intersect(a: AABB, b: AABB) -> AABB:
    return AABB(
        max(a.x0, b.x0), max(a.y0, b.y0),
        min(a.x1, b.x1), min(a.y1, b.y1))

def aabb_union(a: AABB, b: AABB) -> AABB:
    return AABB(
        min(a.x0, b.x0), min(a.y0, b.y0),
        max(a.x1, b.x1), max(a.y1, b.y1))

def size_with_aabb_and_center(aabb: AABB, center: Point2) -> tuple[Point2, Point2]:
    """
    Produces the dimensions of the smallest new image which completely contains
    aabb and has center as its geometric center.

    The first point returned is the new width/height. The second point returned
    is the offsets that need to be added to the current image within that
    width/height, if we were going to paste the aabb into the new image.
    """

    left_d = center.x - aabb.x0
    right_d = aabb.x1 - center.x

    up_d = center.y - aabb.y0
    down_d = aabb.y1 - center.y

    new_w = max(left_d, right_d) * 2 + 1
    new_h = max(up_d, down_d) * 2 + 1
    off_x = right_d - left_d if right_d > left_d else 0
    off_y = down_d - up_d if down_d > up_d else 0

    return (
        Point2(new_w, new_h),
        Point2(off_x, off_y),
    )

def recenter_with_aabb(img: Image.Image, aabb: AABB, center: Point2) -> Image.Image:
    new_size, new_off = size_with_aabb_and_center(aabb, center)

    new_img = Image.new(img.mode, (new_size.x, new_size.y))
    new_img.paste(
        img.crop((
            aabb.x0, aabb.y0, aabb.x1, aabb.y1)),
        (new_off.x, new_off.y))

    return new_img


def calc_intersect_center(img1: Image.Image, img2: Image.Image, both_aabb: AABB) -> Point2:
    """
    Given two images, find the average of their intersection pixels (i.e. the
    pixels for which they both have non-zero alpha).

    Only search inside both_aabb.
    """
    intersect_pixels: list[tuple[int, int]] = []

    for x in range(both_aabb.x0, both_aabb.x1+1):
        for y in range(both_aabb.y0, both_aabb.y1+1):
            if img1.getpixel((x, y))[3] != 0 and \
                img2.getpixel((x, y))[3] != 0:
                intersect_pixels.append((x, y))

    return Point2.round_from_float_tuple((
        sum([x[0] for x in intersect_pixels]) / len(intersect_pixels),
        sum([x[1] for x in intersect_pixels]) / len(intersect_pixels)))


def draw_debug_dot(img: Image.Image, pos: Point2):
    for i in range(-3, 4):
        for j in range(-3, 4):
            img.putpixel((
                pos[0] + i,
                pos[1] + j,
            ), (255,0,255))


def auto_white_balance(img: Image.Image) -> Image.Image:
    imcenter_x, imcenter_y = (img.width / 2, img.height / 2)

    # strip alpha channel as it doesn't participate in this color space transform
    data = np.asarray(img)[:,:,:3]

    refpos = (round(img.width / 2), round(img.height / 2)) # center of image
    refsize = (32,32) # reference sample size

    sub = data #data[refpos[0]:refpos[0]+refsize[0],refpos[1]:refpos[1]+refsize[1]]
    Image.fromarray(sub) 
    c = list(np.mean(data[:,:,i]) for i in range(3))
    wb = data.astype(float)

    # RGB scaling just decreases oversaturated components to get average grey
    # See https://en.wikipedia.org/wiki/Color_balance

    for i in range(3):
        wb[:,:,i] /= c[i]/float(min(c))
        
    # Conversion functions courtesy of https://stackoverflow.com/a/34913974/2721685
    def rgb2ycbcr(im):
        xform = np.array([[.299, .587, .114], [-.1687, -.3313, .5], [.5, -.4187, -.0813]])
        ycbcr = im.dot(xform.T)
        ycbcr[:,:,[1,2]] += 128
        return ycbcr #np.uint8(ycbcr)

    def ycbcr2rgb(im):
        xform = np.array([[1, 0, 1.402], [1, -0.34414, -.71414], [1, 1.772, 0]])
        rgb = im.astype(float)
        rgb[:,:,[1,2]] -= 128
        rgb = rgb.dot(xform.T)
        np.putmask(rgb, rgb > 255, 255)
        np.putmask(rgb, rgb < 0, 0)
        return np.uint8(rgb)

    # Convert data and sample to YCbCr
    ycbcr = rgb2ycbcr(data)
    ysub = rgb2ycbcr(sub)

    # Calculate mean components
    yc = list(np.mean(ysub[:,:,i]) for i in range(3))

    # Center cb and cr components of image based on sample
    for i in range(1,3):
        ycbcr[:,:,i] = np.clip(ycbcr[:,:,i] + (128-yc[i]), 0, 255)

    rgb = ycbcr2rgb(ycbcr) # Convert back

    ret = Image.fromarray(rgb)
    ret.putalpha(img.getchannel("A"))

    return ret