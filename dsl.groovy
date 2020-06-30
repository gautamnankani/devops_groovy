job("job1") {
  scm {
        github('gautamnankani/test555', 'dev1')
    }
  triggers {
        scm('* * * * *')
  }
}

job("job2") {
  triggers {
    upstream {
      upstreamProjects('job1')
    }
  }
  steps {
    sshBuilder {
      siteName("root@192.168.99.101:22")
      command("""if [[ -n \$(ls | grep webserver_kube) ]]
then
   rm -rf /webserver_kube/
else
   sudo git clone https://github.com/gautamnankani/webserver_kube.git
fi
sudo bash webserver_kube/deploy.sh
sudo bash webserver_kube/copy.sh
""")
      execEachLine(false)
    }
  }
}

job("job3") {
  triggers {
    upstream {
      upstreamProjects('job2')
    }
  }
  steps {
    shell {
      command("""
rm -f play.properties
touch play.properties
html_port=30000
php_port=31000
flag=0
for x in \$(ls ../job1/.)
do
  if [[ -n \$(echo \$x | grep [.]html) ]]
  then
     port=\$html_port
  else
     port=\$php_port
  fi
  
  echo \$(curl -o /dev/null -s -w '%{http_code}' 192.168.99.100:\$port/\$x) > code
  
  y=\$(cat code)
  
  if [ \$y == 200 ]
  then 
     echo "everything is ok"
  else 
     echo "something wrong at \$port and \$x"
     flag=1
  fi
done
      
if [ \$flag == 0 ]
then
    echo status="good" > play.properties
else
    # if something goes wrong it will exit with success, so nested job can mail about error
    echo status="bad" > play.properties
fi
      """)
    }
    environmentVariables {
      propertiesFile("play.properties")
    }
  }
  publishers {
    downstreamParameterized {
      trigger("job4") {
        condition("SUCCESS")
        parameters {
          predefinedBuildParameters {
            properties("Status=\$status")
            textParamValueOnNewLine(true)
          }
        }
        triggerWithNoParameters(triggerWithNoParameters = false)
      }
    }
  }
}


job("job4") {
  parameters {
    stringParam {
      name("Status")
      defaultValue(null)
      description("Status for mail")
      trim(false)
    }
  }
  steps {
    shell {
      command("""
if [ \$Status == "bad" ]
then
  ./sendmail.py
else
  echo "everything looks fine"
fi
""")
    }
  }
}

buildPipelineView('pipeline') {
    filterBuildQueue()
    filterExecutors()
    title('Project A CI Pipeline')
    displayedBuilds(5)
    selectedJob('job1')
    alwaysAllowManualTrigger()
    showPipelineParameters()
    refreshFrequency(60)
}
