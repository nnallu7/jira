import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.filter.SearchRequestService
import com.atlassian.jira.issue.search.SearchRequest
import com.atlassian.jira.bc.user.search.UserSearchParams
import com.atlassian.jira.bc.user.search.UserSearchService
import java.lang.StringBuilder

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import groovy.json.JsonSlurper;

// Get value of product area of each project
def baseurl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl");

// Defining scripts dir path based on the environments
def script_path = "/var/atlassian/application-data/jira/scripts";
if(baseurl.contains('prodjira')) {
    script_path = "/d2/shareddata/scripts";
}

// Defining object to access the common functions in Utility file
GroovyShell shell = new GroovyShell();
def utilsObj = shell.parse(new File("${script_path}/CommonModule/Utils.groovy"));

//Enter the Text you want to search in the filter below ''
def str = 'Phased'
def i = 1;
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

content += """
    <table id="derived_field_1">
    <tr class="_epic_1">
     <th class="_header_1">S.No</th>
     <th class="_header_1">Filter Link</th>
     <th class="_header_1">Filter Owner</th>
     <th class="_header_1">Query</th>
     <th class="_header_1">Private/Shared</th>
    </tr>
    """;

content += "Hi,<br><br>Please find the list of JIRA Filters whose query contains word 'Feature Type'.<br><br>";


SearchRequestService searchRequestService = ComponentAccessor.getComponent(SearchRequestService.class)
UserSearchService userSearchService = ComponentAccessor.getComponent(UserSearchService)
StringBuilder output = StringBuilder.newInstance()
output << "Output : <pre>\n"


UserSearchParams userSearchParams = new UserSearchParams.Builder()
    .allowEmptyQuery(true)
    .ignorePermissionCheck(true)
    .maxResults(10000)
    .build()
userSearchService.findUsers("", userSearchParams).each{ApplicationUser filter_owner ->
    try {
        searchRequestService.getOwnedFilters(filter_owner).each{SearchRequest filter->
            String jql = filter.getQuery().toString()
            if (jql.contains(str)) {
                
                def url = """<a href="https://jira_base_url/issues/?filter=${filter.id}>${filter.id}</a>"""
                
                content += """
                <tr class="_epic_1">
                <td class="_epic_1">${i}</td>
                <td class="_epic_1"><a href=https://jira_base_url/issues/?filter=${filter.id}>${filter.name}</a></td>
                <td class="_epic_1">${filter_owner.displayName}</td>
                <td class="_epic_1">${jql}</td>
                <td class="_epic_1">${filter.getPermissions().isPrivate() ? 'Private' : 'Shared'}</td>"""
                i = i + 1;
                content += "</tr>"
            }   
        }
    } catch (Exception e) {
           output << "Unable to get filters for ${filter_owner.displayName} due to ${e}"     
    } 
}
content += "</table>";

// Email the content
def subject = "List of JIRA Filters whose query contains key word 'Feature Type'";

def emailAddr = "nnallusamy@gmail.com";

utilsObj.sendEmail (emailAddr, subject, content);

