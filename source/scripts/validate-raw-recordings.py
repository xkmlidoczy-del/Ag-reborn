#!/usr/bin/env python3
"""Read-only full decoding/probing plus clearly separate representative frame exports."""
import json, pathlib, subprocess, sys, hashlib

root = pathlib.Path(sys.argv[1])
out = root / 'validation'
out.mkdir(exist_ok=True)
index = out / 'all-recording-validation.json'
records = json.loads(index.read_text()) if index.exists() else []
clips = [root / name for name in sys.argv[2:]] if len(sys.argv) > 2 else sorted(root.glob('*.mp4'))
for clip in clips:
    assert clip.parent == root and clip.suffix == '.mp4' and clip.is_file(), clip
    probe = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(clip)
    ]))
    video = [s for s in probe['streams'] if s['codec_type'] == 'video']
    assert len(video) == 1 and video[0]['codec_name'] == 'h264', clip
    assert video[0]['width'] == 1046 and video[0]['height'] == 720, clip
    assert not any(s['codec_type'] == 'audio' for s in probe['streams']), clip
    decoded = subprocess.run([
        'ffmpeg', '-v', 'error', '-threads', '2', '-i', str(clip), '-f', 'null', '-'
    ], capture_output=True, text=True)
    assert decoded.returncode == 0 and not decoded.stderr.strip(), (clip, decoded.stderr)
    duration = float(probe['format']['duration'])
    assert duration > 15, clip
    frame = out / (clip.stem + '-probe.png')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', str(min(10, duration/2)),
                    '-i', str(clip), '-frames:v', '1', str(frame)], check=True)
    from PIL import Image, ImageStat
    stats = ImageStat.Stat(Image.open(frame).convert('RGB'))
    assert max(stats.mean) > 10 and max(stats.stddev) > 10, (clip, stats.mean)
    records = [record for record in records if record['file_name'] != clip.name]
    records.append({'file_name': clip.name, 'bytes': clip.stat().st_size,
                    'sha256': hashlib.sha256(clip.read_bytes()).hexdigest(),
                    'duration_seconds': duration, 'width':1046, 'height':720,
                    'container_frame_rate':video[0]['r_frame_rate'], 'silent':True,
                    'full_decode':'passed', 'nonblack_probe':'passed'})
    records.sort(key=lambda record: record['file_name'])
    index.write_text(json.dumps(records, indent=2)+'\n')
    print(clip.name, duration, 'decoded/nonblack', flush=True)
