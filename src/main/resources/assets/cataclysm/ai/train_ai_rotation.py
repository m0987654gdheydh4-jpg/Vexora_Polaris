import argparse
import json
import math
import random
import subprocess
import sys
from pathlib import Path


FEATURES = [
    "targetDeltaYaw",
    "targetDeltaPitch",
    "distance",
    "timeSinceLastHitMs",
    "fallDistance",
    "diffY",
    "prevTargetYaw",
    "prevTargetPitch",
    "prevYaw",
    "prevPitch",
    "prevDistance",
    "sensitivity",
    "gcd",
    "yawSpeed",
    "pitchSpeed",
    "yawAimProgress",
    "pitchAimProgress",
    "totalAimProgress",
    "playerSpeedXZ",
    "targetSpeedXZ",
    "playerVelocityY",
    "targetVelocityY",
    "targetId",
    "targetHurtTime",
    "targetAge",
    "playerOnGround",
    "playerSprinting",
    "playerSneaking",
    "playerUsingItem",
    "targetWidth",
    "targetHeight",
    "strictDistance",
]


def load_catboost(auto_install):
    try:
        from catboost import CatBoostRegressor, Pool
        return CatBoostRegressor, Pool
    except ImportError:
        if not auto_install:
            raise
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--user", "catboost"])
        from catboost import CatBoostRegressor, Pool
        return CatBoostRegressor, Pool


def number(value, default=0.0):
    try:
        result = float(value)
    except (TypeError, ValueError):
        return default
    if not math.isfinite(result):
        return default
    return result


def empty_stats():
    return {
        "lines": 0,
        "records": 0,
        "accepted": 0,
        "jsonErrors": 0,
        "badRecords": 0,
        "badTargets": 0,
        "oversizedDelta": 0,
        "candidatePhase": 0,
        "legacyPhase": 0,
        "lowQuality": 0,
        "badHitAim": 0,
    }


def parse_records(path, args):
    rows = []
    stats = empty_stats()
    with path.open("r", encoding="utf-8") as file:
        for raw in file:
            raw = raw.strip()
            if not raw:
                continue
            stats["lines"] += 1
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                stats["jsonErrors"] += 1
                continue

            records = parsed if isinstance(parsed, list) else [parsed]
            for record in records:
                stats["records"] += 1
                if not isinstance(record, dict):
                    stats["badRecords"] += 1
                    continue

                phase = str(record.get("attackPhase", "legacy"))
                if phase == "candidate":
                    stats["candidatePhase"] += 1
                    continue
                if phase == "legacy" and not args.allow_legacy:
                    stats["legacyPhase"] += 1
                    continue

                yaw = number(record.get("deltaYaw"), None)
                pitch = number(record.get("deltaPitch"), None)
                if yaw is None or pitch is None:
                    stats["badTargets"] += 1
                    continue
                if abs(yaw) > 90.0 or abs(pitch) > 90.0:
                    stats["oversizedDelta"] += 1
                    continue

                quality = number(record.get("qualityScore"), 1.0)
                if quality < args.min_quality:
                    stats["lowQuality"] += 1
                    continue

                hit_yaw = number(record.get("hitAimYaw"), None)
                hit_pitch = number(record.get("hitAimPitch"), None)
                if hit_yaw is not None and hit_yaw > args.max_hit_yaw:
                    stats["badHitAim"] += 1
                    continue
                if hit_pitch is not None and hit_pitch > args.max_hit_pitch:
                    stats["badHitAim"] += 1
                    continue

                features = [number(record.get(name)) for name in FEATURES]
                rows.append((features, yaw, pitch, quality, phase))

    stats["accepted"] = len(rows)
    return rows, stats


def split_rows(rows, test_ratio, seed):
    shuffled = list(rows)
    random.Random(seed).shuffle(shuffled)
    if test_ratio <= 0.0 or len(shuffled) < 20:
        return shuffled, []
    test_size = max(1, int(len(shuffled) * test_ratio))
    test_size = min(test_size, len(shuffled) - 1)
    return shuffled[test_size:], shuffled[:test_size]


def make_xy(rows, target):
    x = [row[0] for row in rows]
    y_index = 1 if target == "yaw" else 2
    y = [row[y_index] for row in rows]
    return x, y


def train_model(CatBoostRegressor, Pool, train_rows, valid_rows, target, args, out_path):
    train_x, train_y = make_xy(train_rows, target)
    train_pool = Pool(train_x, train_y, feature_names=FEATURES)
    eval_set = None
    if valid_rows:
        valid_x, valid_y = make_xy(valid_rows, target)
        eval_set = Pool(valid_x, valid_y, feature_names=FEATURES)

    model = CatBoostRegressor(
        iterations=args.iterations,
        depth=args.depth,
        learning_rate=args.learning_rate,
        loss_function="RMSE",
        eval_metric="RMSE",
        random_seed=args.seed,
        allow_writing_files=False,
        verbose=args.verbose,
    )
    model.fit(train_pool, eval_set=eval_set, use_best_model=eval_set is not None)
    model.save_model(str(out_path), format="cbm")
    return model.get_best_score()


def write_schema(ai_dir, rows, stats, args):
    payload = {
        "features": FEATURES,
        "featureCount": len(FEATURES),
        "rows": rows,
        "stats": stats,
        "filters": {
            "minQuality": args.min_quality,
            "maxHitYaw": args.max_hit_yaw,
            "maxHitPitch": args.max_hit_pitch,
            "allowLegacy": args.allow_legacy,
        },
        "iterations": args.iterations,
        "depth": args.depth,
        "learningRate": args.learning_rate,
        "seed": args.seed,
        "normalized": False,
    }
    (ai_dir / "feature_schema.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def write_report(ai_dir, rows, train_rows, valid_rows, stats, args, yaw_score=None, pitch_score=None):
    payload = {
        "acceptedRows": rows,
        "trainRows": train_rows,
        "validRows": valid_rows,
        "stats": stats,
        "filters": {
            "minQuality": args.min_quality,
            "maxHitYaw": args.max_hit_yaw,
            "maxHitPitch": args.max_hit_pitch,
            "allowLegacy": args.allow_legacy,
        },
        "models": {
            "yaw": yaw_score,
            "pitch": pitch_score,
        },
    }
    (ai_dir / "training_report.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2, default=str), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ai-dir", required=True)
    parser.add_argument("--dataset", default="kill_aura_dataset.jsonl")
    parser.add_argument("--iterations", type=int, default=1200)
    parser.add_argument("--depth", type=int, default=6)
    parser.add_argument("--learning-rate", type=float, default=0.035)
    parser.add_argument("--test-ratio", type=float, default=0.12)
    parser.add_argument("--seed", type=int, default=1337)
    parser.add_argument("--min-rows", type=int, default=200)
    parser.add_argument("--max-rows", type=int, default=0)
    parser.add_argument("--min-quality", type=float, default=0.35)
    parser.add_argument("--max-hit-yaw", type=float, default=28.0)
    parser.add_argument("--max-hit-pitch", type=float, default=28.0)
    parser.add_argument("--verbose", type=int, default=100)
    parser.add_argument("--allow-legacy", action="store_true")
    parser.add_argument("--no-auto-install", action="store_true")
    args = parser.parse_args()

    ai_dir = Path(args.ai_dir)
    dataset = ai_dir / args.dataset
    if not dataset.is_file():
        raise SystemExit(f"dataset not found: {dataset}")

    CatBoostRegressor, Pool = load_catboost(not args.no_auto_install)
    rows, stats = parse_records(dataset, args)
    if args.max_rows > 0 and len(rows) > args.max_rows:
        rows = random.Random(args.seed).sample(rows, args.max_rows)
        stats["sampledTo"] = args.max_rows
    if len(rows) < args.min_rows:
        write_report(ai_dir, len(rows), 0, 0, stats, args)
        raise SystemExit(f"not enough rows after filters: {len(rows)} < {args.min_rows}")

    train_rows, valid_rows = split_rows(rows, args.test_ratio, args.seed)
    print(
        f"rows={len(rows)} train={len(train_rows)} valid={len(valid_rows)} "
        f"legacy={stats['legacyPhase']} low_quality={stats['lowQuality']} bad_hit_aim={stats['badHitAim']}",
        flush=True,
    )

    yaw_score = train_model(CatBoostRegressor, Pool, train_rows, valid_rows, "yaw", args, ai_dir / "delta_yaw_model.cbm")
    pitch_score = train_model(CatBoostRegressor, Pool, train_rows, valid_rows, "pitch", args, ai_dir / "delta_pitch_model.cbm")
    write_schema(ai_dir, len(rows), stats, args)
    write_report(ai_dir, len(rows), len(train_rows), len(valid_rows), stats, args, yaw_score, pitch_score)
    print("saved delta_yaw_model.cbm and delta_pitch_model.cbm", flush=True)


if __name__ == "__main__":
    main()
