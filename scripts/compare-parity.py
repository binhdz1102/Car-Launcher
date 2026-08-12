#!/usr/bin/env python3
"""Compare baseline and candidate screenshots without non-standard numeric dependencies."""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as error:  # pragma: no cover - command-line dependency diagnostic
    raise SystemExit("Pillow is required: python -m pip install Pillow") from error


def parse_rect(value: str) -> tuple[int, int, int, int]:
    fields = value.split(",")
    if len(fields) != 4:
        raise argparse.ArgumentTypeError("Rectangle must be x,y,width,height")
    try:
        x, y, width, height = (int(field) for field in fields)
    except ValueError as error:
        raise argparse.ArgumentTypeError("Rectangle values must be integers") from error
    if width <= 0 or height <= 0:
        raise argparse.ArgumentTypeError("Rectangle width and height must be positive")
    return x, y, width, height


def allowed_pixel(mask: Image.Image | None, x: int, y: int) -> bool:
    return mask is None or mask.getpixel((x, y)) > 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--mask", type=Path, help="White pixels are compared; black pixels are ignored.")
    parser.add_argument("--ignore-rect", action="append", default=[], type=parse_rect)
    parser.add_argument("--pixel-tolerance", type=int, default=16)
    parser.add_argument("--minimum-ssim", type=float, default=0.98)
    parser.add_argument("--maximum-different-pixel-ratio", type=float, default=0.02)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()

    baseline = Image.open(arguments.baseline).convert("RGB")
    candidate = Image.open(arguments.candidate).convert("RGB")
    if baseline.size != candidate.size:
        raise SystemExit(f"Image dimensions differ: {baseline.size} != {candidate.size}")

    mask = None
    if arguments.mask:
        mask = Image.open(arguments.mask).convert("L")
        if mask.size != baseline.size:
            raise SystemExit(f"Mask dimensions differ: {mask.size} != {baseline.size}")
    if arguments.ignore_rect:
        if mask is None:
            mask = Image.new("L", baseline.size, color=255)
        for x, y, width, height in arguments.ignore_rect:
            for row in range(max(0, y), min(mask.height, y + height)):
                for column in range(max(0, x), min(mask.width, x + width)):
                    mask.putpixel((column, row), 0)

    baseline_pixels = baseline.load()
    candidate_pixels = candidate.load()
    count = 0
    baseline_sums = [0.0, 0.0, 0.0]
    candidate_sums = [0.0, 0.0, 0.0]
    different = 0
    for y in range(baseline.height):
        for x in range(baseline.width):
            if not allowed_pixel(mask, x, y):
                continue
            first = baseline_pixels[x, y]
            second = candidate_pixels[x, y]
            count += 1
            if max(abs(first[index] - second[index]) for index in range(3)) > arguments.pixel_tolerance:
                different += 1
            for index in range(3):
                baseline_sums[index] += first[index]
                candidate_sums[index] += second[index]
    if count == 0:
        raise SystemExit("Mask excludes every pixel.")

    baseline_means = [value / count for value in baseline_sums]
    candidate_means = [value / count for value in candidate_sums]
    baseline_variance = [0.0, 0.0, 0.0]
    candidate_variance = [0.0, 0.0, 0.0]
    covariance = [0.0, 0.0, 0.0]
    for y in range(baseline.height):
        for x in range(baseline.width):
            if not allowed_pixel(mask, x, y):
                continue
            first = baseline_pixels[x, y]
            second = candidate_pixels[x, y]
            for index in range(3):
                first_delta = first[index] - baseline_means[index]
                second_delta = second[index] - candidate_means[index]
                baseline_variance[index] += first_delta * first_delta
                candidate_variance[index] += second_delta * second_delta
                covariance[index] += first_delta * second_delta
    baseline_variance = [value / count for value in baseline_variance]
    candidate_variance = [value / count for value in candidate_variance]
    covariance = [value / count for value in covariance]

    c1 = (0.01 * 255) ** 2
    c2 = (0.03 * 255) ** 2
    channel_ssim = []
    for index in range(3):
        numerator = (2 * baseline_means[index] * candidate_means[index] + c1) * (2 * covariance[index] + c2)
        denominator = (
            (baseline_means[index] ** 2 + candidate_means[index] ** 2 + c1)
            * (baseline_variance[index] + candidate_variance[index] + c2)
        )
        channel_ssim.append(numerator / denominator if denominator else 1.0)
    score = sum(channel_ssim) / len(channel_ssim)
    different_ratio = different / count
    result = {
        "baseline": str(arguments.baseline),
        "candidate": str(arguments.candidate),
        "comparedPixels": count,
        "pixelTolerance": arguments.pixel_tolerance,
        "differentPixels": different,
        "differentPixelRatio": different_ratio,
        "channelSsim": channel_ssim,
        "ssim": score,
        "minimumSsim": arguments.minimum_ssim,
        "maximumDifferentPixelRatio": arguments.maximum_different_pixel_ratio,
        "passed": score >= arguments.minimum_ssim and different_ratio <= arguments.maximum_different_pixel_ratio,
    }
    rendered = json.dumps(result, indent=2)
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
