const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Function to find all TypeScript files
function findTypeScriptFiles(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  
  files.forEach(file => {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    
    if (stat.isDirectory()) {
      findTypeScriptFiles(filePath, fileList);
    } else if (file.endsWith('.ts') || file.endsWith('.tsx')) {
      fileList.push(filePath);
    }
  });
  
  return fileList;
}

// Function to convert TypeScript to JavaScript
function convertToJS() {
  const srcDir = path.join(__dirname, 'src');
  const tsFiles = findTypeScriptFiles(srcDir);
  
  console.log(`Found ${tsFiles.length} TypeScript files to convert...`);
  
  // Create a backup of the original files
  const backupDir = path.join(__dirname, 'src-backup');
  if (!fs.existsSync(backupDir)) {
    fs.mkdirSync(backupDir);
    console.log(`Created backup directory at ${backupDir}`);
  }
  
  // Copy original files to backup
  tsFiles.forEach(file => {
    const relativePath = path.relative(srcDir, file);
    const backupPath = path.join(backupDir, relativePath);
    const backupDirPath = path.dirname(backupPath);
    
    if (!fs.existsSync(backupDirPath)) {
      fs.mkdirSync(backupDirPath, { recursive: true });
    }
    
    fs.copyFileSync(file, backupPath);
  });
  
  console.log('Backup of TypeScript files completed.');
  
  // Convert each TypeScript file to JavaScript
  tsFiles.forEach(file => {
    try {
      // Convert file using Babel
      const outputFile = file.endsWith('.tsx') 
        ? file.replace(/\.tsx$/, '.jsx')
        : file.replace(/\.ts$/, '.js');
      
      execSync(`npx babel ${file} --out-file ${outputFile} --extensions ".ts,.tsx"`);
      
      // Read the converted file
      let content = fs.readFileSync(outputFile, 'utf8');
      
      // Remove TypeScript specific syntax
      content = content
        // Remove type imports
        .replace(/import type .*? from ['"].*?['"];?\n?/g, '')
        // Remove type exports
        .replace(/export type .*?;?\n?/g, '')
        // Remove type annotations
        .replace(/\:\s*[\w\[\]{}|<>]+(?=,|;|\s*=[^,;])/g, '')
        // Remove interface and type declarations
        .replace(/(export\s+)?(interface|type)\s+\w+\s*({[^}]*}|=.*?);?\n?/g, '')
        // Remove type assertions (as Type)
        .replace(/\s+as\s+[\w\[\]{}|<>]+/g, '');
      
      // Write the cleaned content back
      fs.writeFileSync(outputFile, content, 'utf8');
      
      // Remove the original TypeScript file
      fs.unlinkSync(file);
      
      console.log(`Converted ${file} to ${outputFile}`);
    } catch (error) {
      console.error(`Error converting ${file}:`, error.message);
    }
  });
  
  console.log('Conversion completed!');
  console.log(`Original TypeScript files have been backed up to ${backupDir}`);
}

// Run the conversion
convertToJS();
