package com.soasta.jenkins.CloudTestServer;

f=namespace(lib.FormTagLib)

f.section(title:"CloudTest Servers") {
    f.block {
        f.repeatableProperty(field:"servers",noAddButton:true,minimum:1)
    }
}