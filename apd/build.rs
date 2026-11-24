use std::env;
use std::fs::File;
use std::io::Write;
use std::path::Path;

/// 二开：apd 版本必须与管理器 versionCode/versionName 完全一致。
///
/// 版本规则是日期 + 当天序号：2026-11-24 第 1 个包 = 26112401 / v26.11.24_1。
/// 根目录 build.gradle.kts 的 getVersionCode() / getVersionName() 必须同步修改，
/// 否则 `apd -V` 与管理器版本不等，状态会停在 ANDROIDPATCH_NEED_UPDATE，
/// 底栏「系统模块」入口消失（MainActivity 只认 ANDROIDPATCH_INSTALLED）。
///
/// 这个版本号与 KernelPatch 无关；kpimg / kernelPatchVersion 不变时，
/// 内核补丁卡片保持原样，不会提示重新修补 boot。
const VERSION_CODE: u32 = 26112401;
const VERSION_NAME: &str = "v26.11.24_1";

fn main() {
    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    let out_dir = Path::new(&out_dir);

    File::create(out_dir.join("VERSION_CODE"))
        .expect("Failed to create VERSION_CODE")
        .write_all(VERSION_CODE.to_string().as_bytes())
        .expect("Failed to write VERSION_CODE");

    File::create(out_dir.join("VERSION_NAME"))
        .expect("Failed to create VERSION_NAME")
        .write_all(VERSION_NAME.as_bytes())
        .expect("Failed to write VERSION_NAME");
}
