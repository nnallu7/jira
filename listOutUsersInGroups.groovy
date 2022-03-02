/**
 * This script is to list out users in AD groups
 */
import groovyx.net.http.HTTPBuilder;
import groovy.json.JsonSlurper;

import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.component.ComponentAccessor;

import groovy.sql.Sql;
import java.sql.Connection;
import org.ofbiz.core.entity.ConnectionFactory;
import org.ofbiz.core.entity.DelegatorInterface;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import groovy.json.JsonSlurper;


log.setLevel(org.apache.log4j.Level.DEBUG);

// Get list of project keys from JIRA
def projectListInJira = ComponentAccessor.getProjectManager().getProjectObjects();
def jiraProjectKeys = [];
def array = []

projectListInJira.each { project ->
    jiraProjectKeys.push(project.key);
}

// Get value of product area of each project
def baseurl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
def remote = new HTTPBuilder(baseurl);

// Defining scripts dir path based on the environments
def script_path = "/var/atlassian/application-data/jira/scripts";
if(baseurl.contains('athenajira')) {
    script_path = "/d2/shareddata/scripts";
}

// Defining object to access the common functions in Utility file
GroovyShell shell = new GroovyShell();
def utilsObj = shell.parse(new File("${script_path}/CommonModule/Utils.groovy"));

def jsonSlurper = new JsonSlurper();
def data = jsonSlurper.parse(new File("${script_path}/sd_project_list/sd_project_list_for_change_ticket.json"));

def project_uses_core_sre_group = data["Core SRE Approvers"] as List;
def project_uses_SRE_Triage_Approvers = data["SRE Triage Approvers"] as List;

// Group active projects by Product Area and Project Classification
jiraProjectKeys.each { projectKey ->

    def customFldManager = ComponentAccessor.getCustomFieldManager();
    def projectClassification = customFldManager.getCustomFieldObject("customfield_16500");

    // Query for all copy up tasks which are not yet ready for QAWatertown
    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser);
    def searchService = ComponentAccessor.getComponent(SearchService);
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();

    //def key = projectKey.toString()
    def queryString = "project = ${projectKey}"

    def query = jqlQueryParser.parseQuery(queryString);
    def search = searchService.search(user, query, PagerFilter.getUnlimitedFilter());


    if(search.results.size() > 0) {
        def issue = search.results[0]
        def productAreaValue;
        def projectClassificationValue

        try{
            projectClassificationValue = issue.getCustomFieldValue(projectClassification).toString();
        } catch(Exception e) {
            log.warn("Error getting Product Area : ${projectKey} - ${e}")
        }

        if(projectClassificationValue == "Change Request") {
            
            array.push(projectKey)
        }


    }

}

array.push('SDRM')

def content = """
    <style>
    #derived_field_1
    { 
     
        width: 100%;
        border-radius: 13px; 
    }
    #derived_field_1, ._epic_1, ._header_1
    { 
        font-family: Arial,sans-serif;
        padding: 8px;
        text-align: left;
    }
    ._header_1
    { 
        background-color: #3266A2;
        color: white;
    }
    #derived_field_1 tr:nth-child(even)
    { 
        background-color: #f2f2f2;
    }
    #derived_field_1 tr:nth-child(odd) 
    {
        background-color: #FFFFFF;
    }
    </style>
"""

content += """Hi, Please find the AD Group details related to Change Management project.\n\n\n"""

content += """
    <table id="derived_field_1">
    <tr class="_epic_1">
     <th class="_header_1">S.No</th>
     <th class="_header_1">Project</th>
     <th class="_header_1">Project Key</th>
     <th class="_header_1">Peer Approvers</th>
     <th class="_header_1">No.of Peer Approvers</th>
     <th class="_header_1">SME Approvers</th>
     <th class="_header_1">No.of SME Approvers</th>
     <th class="_header_1">Any Difference between Peer & SME Approvers List</th>
    </tr>
    """;

def count = 1;

array.each { key ->
    
    def projectkey = key.toString()
    def groupManager = ComponentAccessor.getGroupManager()
	def userManager = ComponentAccessor.getUserManager()
    
    def peer_approvers = []
    def sme_approvers = []
    
    if(project_uses_core_sre_group.contains(projectkey)) {
        peer_approvers = groupManager.getUsersInGroup("Core SRE Approvers");
        sme_approvers = groupManager.getUsersInGroup("Core SRE Approvers");
    } else {
        if(project_uses_SRE_Triage_Approvers.contains(projectkey)) {
            peer_approvers = groupManager.getUsersInGroup("SRE Triage Approvers");
            sme_approvers = groupManager.getUsersInGroup("SRE Triage Approvers");
        } else {
            peer_approvers = groupManager.getUsersInGroup("${projectkey}_Peer_Approvers");
            sme_approvers = groupManager.getUsersInGroup("${projectkey}_SME_Approvers");
        }
    }
    
    def peerApprovers = [];
    peer_approvers.each { u ->
        peerApprovers.push(u.getDisplayName())
    }
    
    def smeApprovers = [];
    sme_approvers.each { u ->
        smeApprovers.push(u.getDisplayName())
    }
    
    def no_of_peer_approvers = peer_approvers.size()
    def no_of_sme_approvers = sme_approvers.size()
    
    def projectName = ComponentAccessor.getProjectManager().getProjectObjByKey(projectkey).getName();
    def project_url = """<a href="${baseurl}/projects/${key}/issues/">${projectName}</a>""";
    
    content += """
                <tr class="_epic_1">
                <td class="_epic_1">${count}</td>
                <td class="_epic_1">${project_url}</td>
                <td class="_epic_1">${projectkey}</td>
                <td class="_epic_1">${peerApprovers.join(', ')}</td>
                <td class="_epic_1">${no_of_peer_approvers}</td>
                <td class="_epic_1">${smeApprovers.join(', ')}</td>
                <td class="_epic_1">${no_of_sme_approvers}</td>"""
    
    if(peerApprovers != smeApprovers) {
        content += """<td class="_epic_1"><p style='color:red;'>Yes</p></td>"""
    } else {
        content += """<td class="_epic_1">No</td>"""
    }
    
    content += "</tr>"
    
    count += 1;
    
}

content += "</table>";

// Email the content
def subject = "Change Management Project Related User Groups";

def emailAddr = "atlassianAdmins@athenahealth.com";

utilsObj.sendEmail (emailAddr, subject, content);

