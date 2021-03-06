/*
 * This is a built-in script is used by the connector creation wizard.
 * Do not run it manually!!
 */
 
import groovy.text.*
import java.io.File

//Arguments from Ant:
BUNDLE_DIR = args[0].replaceAll("\\\\", "/")
PACKAGE_NAME = args[1]
RESOURCE_NAME = args[2]
CONNECTOR_NAME = PACKAGE_NAME + "." + RESOURCE_NAME + "Connector"

/* 
 * Add, update, or remove SPI Operations in this map:
 */
spiOps = [ 1 : "AuthenticateOp",
           2 : "ResolveUsernameOp",
           3 : "CreateOp",
           4 : "DeleteOp",
           5 : "SchemaOp",
           6 : "ScriptOnConnectorOp",
           7 : "ScriptOnResourceOp",
           8 : "SearchOp<String>",
           9 : "SyncOp",
          10 : "TestOp",
          11 : "UpdateOp",
          12 : "UpdateAttributeValuesOp" ]

ant.echo ""
ant.echo "Choose which SPI operations the " + RESOURCE_NAME + " connector will implement:"
ant.echo ""
ant.echo "    [0] All Operations"
for(int i in 1..spiOps.size()) {
    String opName = spiOps.get(i)
    int idx = opName.indexOf("Op")
    opName = opName.substring(0, idx)
    ant.echo "    [${i}] ${opName}" 
}
ant.echo ""
ant.input(message:"Enter your selection(s) in a comma-separated list [0-" + spiOps.size() + "]:", addproperty:"selected.ops")
selectedOps = properties['selected.ops'].tokenize(",")

//generate a set of unique operations from user input (eliminate duplicates)
operations = [] as Set
for(s in selectedOps) {
    int i = s.trim().toInteger() 
    if(i == 0) {
        selectedOps = 1..spiOps.size() //all operations
        operations.clear()
        operations.addAll(selectedOps)
        break
    }else if(i in 1..spiOps.size()) {
        operations.add(i)
    }else {
        ant.fail "You entered a value outside of the [0-${spiOps.size()}] range."
    }
}
      
//assemble list of actual interface names                   
interfaces = []
operations.each {
    iName = spiOps[it]
    interfaces.add(iName)
}
if(interfaces.contains("UpdateAttributeValuesOp") && interfaces.contains("UpdateOp")) {
	interfaces.remove("UpdateOp")
	ant.echo "Omitting 'UpdateOp' because 'UpdateAttributeValuesOp' was selected..."
}

baseDir = properties["basedir"]
jarVersion = "1.0"

//map that the template engine will use
values = ["userName":properties["user.name"], "packageName":PACKAGE_NAME, "resourceName":RESOURCE_NAME, "frameworkDir":baseDir, "bundleDir":new File(BUNDLE_DIR).getAbsolutePath(), "interfaces":interfaces, "jarVersion":jarVersion, "resourceNameLower":RESOURCE_NAME.toLowerCase()]
templates = new File(baseDir, "templates").listFiles({ return !it.isDirectory() } as FileFilter).toList()
print templates

packagePath = PACKAGE_NAME.replaceAll("\\.", "/") + "/"
srcPath = BUNDLE_DIR + "/src/main/java/" + packagePath
testPath = BUNDLE_DIR + "/src/test/java/" + packagePath

//create directories
ant.mkdir(dir:srcPath)
ant.mkdir(dir:testPath)
ant.mkdir(dir:BUNDLE_DIR + "/src/test/config")
ant.mkdir(dir:BUNDLE_DIR + "/src/test/config/" + CONNECTOR_NAME + "/config")
ant.mkdir(dir:BUNDLE_DIR + "/lib/build")
ant.mkdir(dir:BUNDLE_DIR + "/lib/test")

engine = new SimpleTemplateEngine()
templates.each {
    result = engine.createTemplate(it).make(values)
    fName = it.getName()
    File f = null
    if(fName.endsWith(".template")) {
        fName = fName.substring(0, fName.length() - ".template".length());
    }
    if(fName.endsWith("Tests.java")) {
        f = new File(testPath, RESOURCE_NAME + fName)
    }else if(fName.endsWith(".java")) {
        f = new File(srcPath, RESOURCE_NAME + fName)
    }else if(fName.startsWith("Messages")) {
        f = new File(srcPath, fName)
    }else if(fName.startsWith("config.groovy")) {
        f = new File(BUNDLE_DIR + "/src/test/config/" + CONNECTOR_NAME + "/config", "config.groovy")
    }else {
        f = new File(BUNDLE_DIR, fName)
    }    
    ant.echo "Writing file: " + f?.toString()
    result.writeTo(f.newWriter())
}
