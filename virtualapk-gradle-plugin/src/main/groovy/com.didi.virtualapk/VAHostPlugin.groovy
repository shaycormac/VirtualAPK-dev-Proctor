package com.didi.virtualapk

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.didi.virtualapk.utils.FileUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * VirtualAPK gradle plugin for host project,
 * The primary role of this class is to save the
 * information needed to build the plugin apk.
 *
 * @author zhengtao
 */
public class VAHostPlugin implements Plugin<Project> {

    Project project
    File vaHostDir;

    @Override
    public void apply(Project project) {

        this.project = project

        //The target project must be a android application module
        if (!project.plugins.hasPlugin('com.android.application')) {
            System.err.println("application required!");
            return;
        }

        vaHostDir = new File(project.getBuildDir(), "VAHost")

        project.afterEvaluate {

            project.android.applicationVariants.each { ApplicationVariant variant ->
                generateDependencies(variant)
                backupHostR(variant)
                backupProguardMapping(variant)
                //keepResourceIds(variant)
            }
        }

    }

    /**
     * Generate ${project.buildDir}/VAHost/versions.txt
     */
    def generateDependencies(ApplicationVariant applicationVariant) {

        applicationVariant.javaCompile.doLast {

            FileUtil.saveFile(vaHostDir, "allVersions", {
                List<String> deps = new ArrayList<String>()
                project.configurations.each {
                    String configName = it.name

                    it.resolvedConfiguration.resolvedArtifacts.each {
                        deps.add("${configName} -> ${it.moduleVersion.id}")
                    }
                }
                Collections.sort(deps)
                return deps
            })

            FileUtil.saveFile(vaHostDir, "versions", {
                List<String> deps = new ArrayList<String>()
                project.configurations.getByName("_${applicationVariant.name}Compile").resolvedConfiguration.resolvedArtifacts.each {
                    deps.add("${it.moduleVersion.id} ${it.file.length()}")
                }
                Collections.sort(deps)
                return deps;
            });
        }

    }

    /**
     * Save R symbol file
     */
    def backupHostR(ApplicationVariant applicationVariant) {

        final ProcessAndroidResources aaptTask = this.project.tasks["process${applicationVariant.name.capitalize()}Resources"]

        aaptTask.doLast {
            project.copy {
                from new File(aaptTask.textSymbolOutputDir, 'R.txt')
                into vaHostDir
                rename { "Host_R.txt" }
            }
        }
    }

    /**
     * Save proguard mapping
     */
    def backupProguardMapping(ApplicationVariant applicationVariant) {

        if (applicationVariant.buildType.minifyEnabled) {
            TransformTask proguardTask = project.tasks["transformClassesAndResourcesWithProguardFor${applicationVariant.name.capitalize()}"]

            ProGuardTransform proguardTransform = proguardTask.transform
            File mappingFile = proguardTransform.mappingFile

            proguardTask.doLast {
                project.copy {
                    from mappingFile
                    into vaHostDir
                }
            }
        }

    }

    /**
     * Keep the host app resource id same with last publish, in order to compatible with the published plugin
     */
     def keepResourceIds(variant) {


        def VIRTUAL_APK_DIR = new File([project.rootDir, 'virtualapk'].join(File.separator))
        System.println("keepResource start")
        def mergeResourceTask = project.tasks["merge${variant.name.capitalize()}Resources"]
        def vaDir = new File(VIRTUAL_APK_DIR, "${variant.dirName}")

        def rSymbole = new File(vaDir, 'Host-R.txt')
        if (!rSymbole.exists()) {
            return
        }

        File resDir = new File(project.projectDir, ['src', 'main', 'res'].join(File.separator))
        File mergedValuesDir = new File(mergeResourceTask.outputDir, 'values')

        mergeResourceTask.doFirst {
            generateIdsXml(rSymbole, resDir)
        }

        mergeResourceTask.doLast {

            def mergeXml = new File(variant.mergeResources.incrementalFolder, 'merger.xml')
            def typeEntries = [:] as Map<String, Set>

            collectResourceEntries(mergeXml, resDir.path, typeEntries)

            generatePublicXml(rSymbole, mergedValuesDir, typeEntries)

            new File(resDir, 'values/ids.xml').delete()
        }
    }


    def collectResourceEntries(final File mergeXml, final String projectResDir, final Map typeEntries) {

        collectAarResourceEntries(null, projectResDir, mergeXml, typeEntries)

        File aarDir = new File(project.buildDir, "intermediates/exploded-aar")

        project.configurations.compile.resolvedConfiguration.resolvedArtifacts.each {
            if (it.extension == 'aar') {
                def moduleVersion = it.moduleVersion.id
                def resPath = new File(aarDir,"${moduleVersion.group}/${moduleVersion.name}/${moduleVersion.version}/res")
                collectAarResourceEntries(moduleVersion.version, resPath.path, mergeXml, typeEntries)
            }
        }
    }


    def collectAarResourceEntries(String aarVersion, String resPath, File mergeXml, final Map typeEntries) {
        final def merger = new XmlParser().parse(mergeXml)
        def filter = aarVersion == null ? {
            it.@config == 'main' || it.@config == 'release'
        } : {
            it.@config = aarVersion
        }
        def dataSets = merger.dataSet.findAll filter
        dataSets.each {
            it.source.each {
                if (it.@path != resPath) {
                    return
                }
                it.file.each {
                    def String type = it.@type
                    if (type != null) {
                        def entrySet = getEntriesSet(type, typeEntries)
                        if (!entrySet.contains(it.@name)) {
                            entrySet.add(it.@name)
                        }
                    } else {
                        it.children().each {
                            type = it.name()
                            def name = it.@name
                            if (type.endsWith('-array')) {
                                type = 'array'
                            } else if (type == 'item'){
                                type = it.@type
                            } else if (type == 'declare-styleable'){
                                return
                            }
                            def entrySet = getEntriesSet(type, typeEntries)
                            if (!entrySet.contains(name)) {
                                entrySet.add(name)
                            }
                        }
                    }
                }
            }
        }
    }

    def generatePublicXml(rSymboleFile, destDir, hostResourceEntries) {
        def styleNameMap = [:] as Map
        def styleEntries = hostResourceEntries['style']
        styleEntries.each {
            def _styleName = it.replaceAll('\\.', '_')
            styleNameMap.put(_styleName, it)
        }

        def lastSplitType
        new File(destDir, "public.xml").withPrintWriter { pw ->
            pw.println "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            pw.println "<resources>"
            rSymboleFile.eachLine { line ->
                def values = line.split(' ')
                def type = values[1]
                if (type == 'styleable') {
                    return
                }
                if (type == 'style') {
                    if (styleNameMap.containsKey(values[2])) {
                        pw.println "\t<public type=\"${type}\" name=\"${styleNameMap.get(values[2])}\" id=\"${values[3]}\" />"
                    }
                    return
                }
                //ID does not filter and remains redundant
                if (type == 'id') {
                    pw.println "\t<public type=\"${type}\" name=\"${values[2]}\" id=\"${values[3]}\" />"
                    return
                }

                //Only keep resources' Id that are present in the current project
                Set entries = hostResourceEntries[type]
                if (entries != null && entries.contains(values[2])) {
                    pw.println "\t<public type=\"${type}\" name=\"${values[2]}\" id=\"${values[3]}\" />"
                } else {
                    if (entries == null) {
                        if (type != lastSplitType) {
                            lastSplitType = type
                            println ">>>> ${type} is splited"
                        }

                    } else {
                        if (type != 'attr'){
                            println ">>>> ${type} : ${values[2]} is deleted"
                        }

                    }
                }

            }
            pw.print "</resources>"
        }
    }

    def generateIdsXml(rSymboleFile, resDir) {
        new File(resDir, "values/ids.xml").withPrintWriter { pw ->
            pw.println "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            pw.println "<resources>"
            rSymboleFile.eachLine { line ->
                def values = line.split(' ')
                if (values[1] == 'id')
                    pw.println "\t<item type=\"id\" name=\"${values[2]}\"/>"
            }
            pw.print "</resources>"
        }
    }


    def Set<String> getEntriesSet (final String type, final Map typeEntries) {
        def entries = typeEntries[type]
        if (entries == null) {
            entries = [] as Set<String>
            typeEntries[type] = entries
        }
        return entries
    }


}
