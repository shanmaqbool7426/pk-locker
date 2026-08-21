const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { createReadStream, createWriteStream } = require('fs');
const { pipeline } = require('stream/promises');

// APK path
const apkPath = path.join(__dirname, 'base.apk');
const outputDir = path.join(__dirname, 'apk_extracted');

// Create output directory
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

// Install adm-zip for extraction
console.log('Installing adm-zip...');
try {
    execSync('npm install adm-zip@0.5.10 --no-save', { stdio: 'inherit', cwd: __dirname });
} catch (e) {
    console.error('Failed to install adm-zip:', e.message);
    process.exit(1);
}

const AdmZip = require('adm-zip');

console.log('\n=== EXTRACTING APK ===');
const zip = new AdmZip(apkPath);
const entries = zip.getEntries();

console.log(`Total entries in APK: ${entries.length}\n`);

// List all entries with sizes
console.log('=== APK CONTENTS ===');
entries.forEach((entry, i) => {
    if (entry.entryName.includes('META-INF') || entry.entryName.endsWith('.dex') || 
        entry.entryName.endsWith('.xml') || entry.entryName.endsWith('.json') ||
        entry.entryName.endsWith('.properties') || entry.entryName.includes('assets/') ||
        entry.entryName.includes('lib/') || entry.entryName.includes('res/') ||
        entry.entryName === 'AndroidManifest.xml') {
        const size = entry.header.size;
        const compressed = entry.header.compressedSize;
        console.log(`[${i+1}] ${entry.entryName} (${size} bytes, compressed: ${compressed})`);
    }
});

// Extract key files
console.log('\n=== EXTRACTING KEY FILES ===');

// Extract AndroidManifest.xml
const manifestEntry = entries.find(e => e.entryName === 'AndroidManifest.xml');
if (manifestEntry) {
    const manifestData = manifestEntry.getData();
    fs.writeFileSync(path.join(outputDir, 'AndroidManifest.xml.bin'), manifestData);
    console.log('AndroidManifest.xml extracted (binary format)');
    
    // Also save as hex for analysis
    const hex = manifestData.toString('hex');
    fs.writeFileSync(path.join(outputDir, 'AndroidManifest.xml.hex'), hex);
    console.log('AndroidManifest.xml hex saved');
}

// Extract all dex files
const dexFiles = entries.filter(e => e.entryName.endsWith('.dex'));
dexFiles.forEach(dex => {
    const data = dex.getData();
    fs.writeFileSync(path.join(outputDir, dex.entryName), data);
    console.log(`Extracted: ${dex.entryName} (${data.length} bytes)`);
});

// Extract META-INF
const metaInf = entries.filter(e => e.entryName.startsWith('META-INF/'));
metaInf.forEach(entry => {
    const data = entry.getData();
    const outPath = path.join(outputDir, entry.entryName);
    const dir = path.dirname(outPath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(outPath, data);
    console.log(`Extracted: ${entry.entryName}`);
});

// Extract assets
const assets = entries.filter(e => e.entryName.startsWith('assets/'));
assets.forEach(entry => {
    const data = entry.getData();
    const outPath = path.join(outputDir, entry.entryName);
    const dir = path.dirname(outPath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(outPath, data);
    console.log(`Extracted: ${entry.entryName} (${data.length} bytes)`);
});

// Extract resources.arsc
const arsc = entries.find(e => e.entryName === 'resources.arsc');
if (arsc) {
    const data = arsc.getData();
    fs.writeFileSync(path.join(outputDir, 'resources.arsc'), data);
    console.log('resources.arsc extracted');
}

// Extract res/ directory
const resFiles = entries.filter(e => e.entryName.startsWith('res/'));
console.log(`\nExtracting ${resFiles.length} resource files...`);
resFiles.forEach(entry => {
    const data = entry.getData();
    const outPath = path.join(outputDir, entry.entryName);
    const dir = path.dirname(outPath);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(outPath, data);
});
console.log('Resources extracted');

// Extract lib/ directory
const libFiles = entries.filter(e => e.entryName.startsWith('lib/'));
if (libFiles.length > 0) {
    console.log(`\nExtracting ${libFiles.length} native library files...`);
    libFiles.forEach(entry => {
        const data = entry.getData();
        const outPath = path.join(outputDir, entry.entryName);
        const dir = path.dirname(outPath);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(outPath, data);
    });
}

// Summary
console.log('\n=== APK ANALYSIS SUMMARY ===');
console.log(`APK Size: ${fs.statSync(apkPath).size} bytes (~${(fs.statSync(apkPath).size / 1024 / 1024).toFixed(2)} MB)`);
console.log(`DEX Files: ${dexFiles.length}`);
dexFiles.forEach(d => console.log(`  - ${d.entryName}: ${(d.header.size / 1024 / 1024).toFixed(2)} MB`));
console.log(`Assets: ${assets.length} files`);
console.log(`Native Libraries: ${libFiles.length} files`);
console.log(`Resource files: ${resFiles.length} files`);

// Check for signing scheme
const v1Sig = metaInf.find(e => e.entryName.endsWith('.RSA') || e.entryName.endsWith('.DSA') || e.entryName.endsWith('.EC'));
const v2v3Block = entries.find(e => e.entryName === 'META-INF/MANIFEST.MF');
console.log(`\nSigning: V1 (JAR signing): ${v1Sig ? 'YES (' + v1Sig.entryName + ')' : 'NO'}`);

console.log('\n=== EXTRACTION COMPLETE ===');
console.log(`All files saved to: ${outputDir}`);
