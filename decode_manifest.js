const fs = require('fs');
const path = require('path');

// Android Binary XML (AXML) Parser
class AXMLParser {
    constructor(buffer) {
        this.buffer = buffer;
        this.position = 0;
        this.strings = [];
        this.resourceIds = [];
    }

    readInt(offset) {
        return this.buffer.readUInt32LE(offset);
    }

    readShort(offset) {
        return this.buffer.readUInt16LE(offset);
    }

    parse() {
        // Check magic number
        const magic = this.readInt(0);
        if (magic !== 0x00080003) {
            throw new Error(`Invalid AXML magic: 0x${magic.toString(16)}`);
        }

        const fileSize = this.readInt(4);
        
        // Parse string pool
        this.parseStringPool();
        
        // Parse resource IDs
        this.parseResourceIds();
        
        // Parse XML nodes
        return this.parseXML();
    }

    parseStringPool() {
        const chunkType = this.readInt(8);
        if (chunkType !== 0x001C0001) {
            throw new Error(`Expected string pool, got: 0x${chunkType.toString(16)}`);
        }

        const chunkSize = this.readInt(12);
        const stringCount = this.readInt(16);
        const styleCount = this.readInt(20);
        const flags = this.readInt(24);
        const stringsOffset = this.readInt(28);
        const stylesOffset = this.readInt(32);

        const isUTF8 = (flags & (1 << 8)) !== 0;

        // Read string offsets
        const stringOffsets = [];
        for (let i = 0; i < stringCount; i++) {
            stringOffsets.push(this.readInt(36 + i * 4));
        }

        // Read strings
        const stringPoolStart = 8 + stringsOffset;
        for (let i = 0; i < stringCount; i++) {
            const offset = stringPoolStart + stringOffsets[i];
            let str = '';
            
            if (isUTF8) {
                // UTF-8 format
                let len = this.buffer.readUInt8(offset);
                if (len & 0x80) {
                    len = ((len & 0x7F) << 8) | this.buffer.readUInt8(offset + 1);
                    let dataLen = this.buffer.readUInt8(offset + 2);
                    str = this.buffer.toString('utf8', offset + 3, offset + 3 + len);
                } else {
                    let dataLen = this.buffer.readUInt8(offset + 1);
                    str = this.buffer.toString('utf8', offset + 2, offset + 2 + len);
                }
            } else {
                // UTF-16 format
                let len = this.readShort(offset);
                if (len & 0x8000) {
                    len = ((len & 0x7FFF) << 16) | this.readShort(offset + 2);
                    str = this.buffer.toString('utf16le', offset + 4, offset + 4 + len * 2);
                } else {
                    str = this.buffer.toString('utf16le', offset + 2, offset + 2 + len * 2);
                }
            }
            this.strings.push(str);
        }
    }

    parseResourceIds() {
        const offset = 8 + this.readInt(12); // After string pool
        const chunkType = this.readInt(offset);
        
        if (chunkType === 0x00080180) {
            // Resource map chunk
            const chunkSize = this.readInt(offset + 4);
            const count = (chunkSize - 8) / 4;
            for (let i = 0; i < count; i++) {
                this.resourceIds.push(this.readInt(offset + 8 + i * 4));
            }
            this.xmlOffset = offset + chunkSize;
        } else {
            this.xmlOffset = offset;
        }
    }

    parseXML() {
        let offset = this.xmlOffset;
        let result = '<?xml version="1.0" encoding="utf-8"?>\n';
        let indent = 0;

        while (offset < this.buffer.length) {
            const chunkType = this.readInt(offset);
            const chunkSize = this.readInt(offset + 4);
            
            if (chunkSize === 0 || offset + chunkSize > this.buffer.length) break;

            switch (chunkType) {
                case 0x00100100: // START_NAMESPACE
                    // Skip namespace declarations
                    break;
                case 0x00100101: // END_NAMESPACE
                    break;
                case 0x00100102: // START_TAG
                    result += '  '.repeat(indent);
                    
                    // Read tag name
                    const nameIdx = this.readInt(offset + 20);
                    const name = this.strings[nameIdx] || `@res${nameIdx}`;
                    
                    // Read attributes
                    const attrCount = this.readShort(offset + 28);
                    const idIdx = this.readShort(offset + 30);
                    const classIdx = this.readShort(offset + 32);
                    const styleIdx = this.readShort(offset + 34);
                    
                    result += `<${name}`;
                    
                    const attrStart = offset + 36;
                    for (let i = 0; i < attrCount; i++) {
                        const attrOffset = attrStart + i * 20;
                        const nsIdx = this.readInt(attrOffset);
                        const attrNameIdx = this.readInt(attrOffset + 4);
                        const rawValueIdx = this.readInt(attrOffset + 8);
                        const typeValue = this.readInt(attrOffset + 12);
                        const dataSize = this.readShort(attrOffset + 16);
                        const dataType = this.buffer.readUInt8(attrOffset + 19);
                        
                        const attrName = this.strings[attrNameIdx] || `attr${attrNameIdx}`;
                        const attrNs = nsIdx !== -1 ? this.strings[nsIdx] : null;
                        
                        let value = '';
                        if (rawValueIdx !== -1 && this.strings[rawValueIdx]) {
                            value = this.strings[rawValueIdx];
                        } else {
                            // Use typed value
                            switch (dataType) {
                                case 0x01: // Reference
                                    value = `@${(typeValue >>> 0).toString(16)}`;
                                    break;
                                case 0x02: // Attribute
                                    value = `?${(typeValue >>> 0).toString(16)}`;
                                    break;
                                case 0x03: // String
                                    value = this.strings[typeValue] || '';
                                    break;
                                case 0x04: // Float
                                    value = Buffer.from(this.buffer.buffer, this.buffer.byteOffset + attrOffset + 12, 4).readFloatLE(0).toString();
                                    break;
                                case 0x10: // Int Dec
                                    value = (typeValue | 0).toString();
                                    break;
                                case 0x11: // Int Hex
                                    value = '0x' + (typeValue >>> 0).toString(16);
                                    break;
                                case 0x12: // Boolean
                                    value = typeValue === 0 ? 'false' : 'true';
                                    break;
                                case 0x1c: // Color
                                    value = '#' + (typeValue >>> 0).toString(16).padStart(8, '0');
                                    break;
                                default:
                                    value = (typeValue >>> 0).toString();
                            }
                        }
                        
                        // Escape XML value
                        value = String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                        
                        if (attrNs) {
                            const prefix = attrNs.includes('android') ? 'android:' : '';
                            result += ` ${prefix}${attrName}="${value}"`;
                        } else {
                            result += ` ${attrName}="${value}"`;
                        }
                    }
                    
                    result += '>\n';
                    indent++;
                    break;
                    
                case 0x00100103: // END_TAG
                    indent = Math.max(0, indent - 1);
                    result += '  '.repeat(indent);
                    const endNameIdx = this.readInt(offset + 20);
                    const endName = this.strings[endNameIdx] || '';
                    result += `</${endName}>\n`;
                    break;
                    
                case 0x00100104: // TEXT
                    const textIdx = this.readInt(offset + 20);
                    const text = this.strings[textIdx] || '';
                    result += '  '.repeat(indent) + text + '\n';
                    break;
            }
            
            offset += chunkSize;
        }

        return result;
    }
}

// Read the binary manifest
const manifestPath = path.join(__dirname, 'apk_extracted', 'AndroidManifest.xml.bin');
const buffer = fs.readFileSync(manifestPath);

try {
    const parser = new AXMLParser(buffer);
    const xml = parser.parse();
    
    // Save decoded manifest
    const outPath = path.join(__dirname, 'apk_extracted', 'AndroidManifest.xml');
    fs.writeFileSync(outPath, xml);
    
    console.log('=== DECODED AndroidManifest.xml ===\n');
    console.log(xml);
    console.log('\n=== Manifest saved to: ' + outPath + ' ===');
} catch (e) {
    console.error('Error parsing manifest:', e.message);
    console.error(e.stack);
}
