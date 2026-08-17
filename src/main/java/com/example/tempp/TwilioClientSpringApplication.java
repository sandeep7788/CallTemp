package com.example.tempp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableScheduling
public class TwilioClientSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwilioClientSpringApplication.class, args);
    }
}

/*<!--sandeep@sandeep-ThinkPad-W530:~/Downloads/site/tempp1 (2)12-1/tempp1$ ./deploy.sh   --jar target/app.jar   --host 13.235.22.151   --key /home/sandeep/Downloads/mainvm.pem   --bucket makecall-jar-bucket   --skip-build

━━━ Checking prerequisites ━━━-->*/
/*

ssh -6 -i mainvm.pem ec2-user@2406:da1a:3.235.248.57
* */


/*
* ./deploy.sh   --jar target/app.jar   --host 3.235.248.57   --key /home/sandeep/Downloads/mainvm.pem   --bucket makecall-jar-bucket   --skip-build
*
* ./deploy.sh \
  --jar target/app.jar \
  --host "2406:da1a:4d8:1d01:9666:e8e5:4a25:1f48" \
  --key /home/sandeep/Downloads/mainvm.pem \
  --bucket makecall-jar-bucket \
  --skip-build
  *
  *
  *
  *
* */