package plugins

package object dependencies {

  type VulnerabilitiesScanned = Iterable[(String, String, List[Vulnerability])]

}
