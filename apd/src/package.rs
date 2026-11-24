use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::{self, BufRead};
use std::path::Path;
use std::thread;
use std::time::Duration;

/// Android 的 uid 分配是 `userId * 100000 + appId`，所以 user 0 的应用 uid 一定 < 100000。
/// `/data/system/packages.list` 只包含 user 0 的条目，分身/多用户条目（如 99910201）
/// 不会被它更新，必须原样保留。
const USER0_UID_LIMIT: i32 = 100_000;

#[derive(Deserialize, Serialize, Clone)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

pub fn read_ap_package_config() -> Vec<PackageConfig> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let file = match File::open("/data/adb/ap/package_config") {
            Ok(file) => file,
            Err(e) => {
                warn!("Error opening file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut reader = csv::Reader::from_reader(file);
        let mut package_configs = Vec::new();
        let mut success = true;

        for record in reader.deserialize() {
            match record {
                Ok(config) => package_configs.push(config),
                Err(e) => {
                    warn!("Error deserializing record: {}", e);
                    success = false;
                    break;
                }
            }
        }

        if success {
            return package_configs;
        }
        thread::sleep(Duration::from_secs(1));
    }
    Vec::new()
}

pub fn write_ap_package_config(package_configs: &[PackageConfig]) -> io::Result<()> {
    let max_retry = 5;
    for _ in 0..max_retry {
        let temp_path = "/data/adb/ap/package_config.tmp";
        let file = match File::create(temp_path) {
            Ok(file) => file,
            Err(e) => {
                warn!("Error creating temp file: {}", e);
                thread::sleep(Duration::from_secs(1));
                continue;
            }
        };

        let mut writer = csv::Writer::from_writer(file);
        let mut success = true;

        for config in package_configs {
            if let Err(e) = writer.serialize(config) {
                warn!("Error serializing record: {}", e);
                success = false;
                break;
            }
        }

        if !success {
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = writer.flush() {
            warn!("Error flushing writer: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }

        if let Err(e) = std::fs::rename(temp_path, "/data/adb/ap/package_config") {
            warn!("Error renaming temp file: {}", e);
            thread::sleep(Duration::from_secs(1));
            continue;
        }
        return Ok(());
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}

fn read_lines<P>(filename: P) -> io::Result<io::Lines<io::BufReader<File>>>
where
    P: AsRef<Path>,
{
    File::open(filename).map(|file| io::BufReader::new(file).lines())
}

pub fn synchronize_package_uid() -> io::Result<()> {
    info!("[synchronize_package_uid] Start synchronizing root list with system packages...");

    let max_retry = 5;
    for _ in 0..max_retry {
        match read_lines("/data/system/packages.list") {
            Ok(lines) => {
                let lines: Vec<_> = lines.filter_map(|line| line.ok()).collect();

                let mut package_configs = read_ap_package_config();

                let system_packages: Vec<String> = lines
                    .iter()
                    .filter_map(|line| line.split_whitespace().next())
                    .map(|pkg| pkg.to_string())
                    .collect();

                let original_len = package_configs.len();
                package_configs.retain(|config| system_packages.contains(&config.pkg));
                let removed_count = original_len - package_configs.len();

                if removed_count > 0 {
                    info!(
                        "Removed {} uninstalled package configurations",
                        removed_count
                    );
                }

                let mut updated = false;

                for line in &lines {
                    let words: Vec<&str> = line.split_whitespace().collect();
                    if words.len() >= 2 {
                        let pkg_name = words[0];
                        if let Ok(uid) = words[1].parse::<i32>() {
                            // packages.list 只包含 user 0；分身/多用户 uid = userId * 100000 + appId
                            // （如 99910201）。按 pkg 直接 find 会命中第一条，把分身条目改写成
                            // 主用户 uid，导致分身授权永久丢失。这里只更新 user 0 的条目。
                            if let Some(config) = package_configs
                                .iter_mut()
                                .find(|config| {
                                    config.pkg == pkg_name && config.uid < USER0_UID_LIMIT
                                })
                            {
                                if config.uid != uid {
                                    info!(
                                        "Updating uid for package {}: {} -> {}",
                                        pkg_name, config.uid, uid
                                    );
                                    config.uid = uid;
                                    updated = true;
                                }
                            }
                        } else {
                            warn!("Error parsing uid: {}", words[1]);
                        }
                    }
                }

                if updated || removed_count > 0 {
                    write_ap_package_config(&package_configs)?;
                }
                return Ok(());
            }
            Err(e) => {
                warn!("Error reading packages.list: {}", e);
                thread::sleep(Duration::from_secs(1));
            }
        }
    }
    Err(io::Error::new(
        io::ErrorKind::Other,
        "Failed after max retries",
    ))
}
