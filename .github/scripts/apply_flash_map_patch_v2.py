from pathlib import Path

base_script_path = Path(".github/scripts/apply_flash_map_patch.py")
base_script = base_script_path.read_text()

old = '''replace_once(\n    "position = cluster.center\\n",\n    "position = clusterCenter\\n",\n    "single cluster center",\n)\nreplace_once(\n    "position = cluster.center\\n",\n    "position = clusterCenter\\n",\n    "multi cluster center",\n)\n'''
new = '''cluster_center_count = text.count("position = cluster.center\\n")\nif cluster_center_count != 2:\n    raise SystemExit(f"cluster centers: expected exactly 2 matches, found {cluster_center_count}")\ntext = text.replace("position = cluster.center\\n", "position = clusterCenter\\n")\n'''

if base_script.count(old) != 1:
    raise SystemExit("base patch script shape changed; refusing to execute")

patched_script = base_script.replace(old, new, 1)
exec(compile(patched_script, str(base_script_path), "exec"))
