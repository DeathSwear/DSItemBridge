import os
import shutil
import subprocess

PROJECT_DIR = r"D:\projects\minecraft\testVersionUp\DSItemBridge"
SRC_JAVA = os.path.join(PROJECT_DIR, r"src\main\java")
SRC_RES = os.path.join(PROJECT_DIR, r"src\main\resources")
TARGET_DIR = os.path.join(PROJECT_DIR, "target")
CLASSES_DIR = os.path.join(TARGET_DIR, "classes")
OUT_JAR = os.path.join(TARGET_DIR, "DSItemBridge.jar")
SERVER_PLUGINS = r"D:\projects\minecraft\testVersionUp\ds_rpg\plugins"

LIBS_DIR = r"D:\projects\minecraft\testVersionUp\ds_rpg\libraries"
MYTHIC_JAR = r"D:\projects\minecraft\testVersionUp\ds_rpg\plugins\MythicMobsPremium-5.13.1-SNAPSHOT.jar"

def build():
    print("=== Building DSItemBridge ===")
    
    # 1. Clean and create target directories
    if os.path.exists(CLASSES_DIR):
        shutil.rmtree(CLASSES_DIR)
    os.makedirs(CLASSES_DIR, exist_ok=True)
    
    # 2. Gather Java files
    java_files = []
    for root, dirs, files in os.walk(SRC_JAVA):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f).replace("\\", "/"))
                
    print(f"Found {len(java_files)} java source files:")
    for jf in java_files:
        print(f"  {os.path.basename(jf)}")
        
    # 3. Gather all libraries from server libraries dir
    lib_jars = [MYTHIC_JAR.replace("\\", "/")]
    for root, dirs, files in os.walk(LIBS_DIR):
        for f in files:
            if f.endswith(".jar"):
                lib_jars.append(os.path.join(root, f).replace("\\", "/"))
                
    print(f"Collected {len(lib_jars)} classpath dependencies.")
    
    # 4. Compile with javac using @options argfile with forward slashes
    argfile_path = os.path.join(TARGET_DIR, "javac_args.txt").replace("\\", "/")
    classes_dir_fwd = CLASSES_DIR.replace("\\", "/")
    with open(argfile_path, "w", encoding="utf-8") as af:
        cp_str = ";".join(lib_jars)
        af.write("-encoding\nUTF-8\n")
        af.write("-cp\n")
        af.write(f"{cp_str}\n")
        af.write("-d\n")
        af.write(f"{classes_dir_fwd}\n")
        af.write("--release\n21\n")
        for jf in java_files:
            af.write(f"{jf}\n")
            
    print("\nRunning javac with argfile...")
    cmd = ["javac", f"@{argfile_path}"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print("COMPILATION FAILED:")
        print(res.stderr)
        return False
    print("Compilation successful!")
    
    # 5. Copy resources into classes dir
    for root, dirs, files in os.walk(SRC_RES):
        for f in files:
            src_f = os.path.join(root, f)
            rel = os.path.relpath(src_f, SRC_RES)
            dst_f = os.path.join(CLASSES_DIR, rel)
            os.makedirs(os.path.dirname(dst_f), exist_ok=True)
            shutil.copy2(src_f, dst_f)
            print(f"Copied resource: {rel}")
            
    # 6. Package JAR
    print(f"\nCreating JAR: {OUT_JAR}")
    jar_cmd = [
        "jar",
        "--create",
        "--file", OUT_JAR,
        "-C", CLASSES_DIR,
        "."
    ]
    res_jar = subprocess.run(jar_cmd, capture_output=True, text=True)
    if res_jar.returncode != 0:
        print("JAR packaging failed:")
        print(res_jar.stderr)
        return False
        
    jar_size = os.path.getsize(OUT_JAR)
    print(f"JAR built successfully: {OUT_JAR} ({jar_size} bytes)")
    
    # 7. Copy to plugins folder
    dest_plugin = os.path.join(SERVER_PLUGINS, "DSItemBridge.jar")
    shutil.copy2(OUT_JAR, dest_plugin)
    print(f"Installed plugin to: {dest_plugin}")
    
    return True

if __name__ == "__main__":
    success = build()
    if not success:
        exit(1)
