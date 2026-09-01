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

ssh -6 -i main1.pem ec2-user@2406:da1a:3.235.248.57
ssh -6 -i mainvm.pem ec2-user@2406:da1a:13.235.248.57
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

/*What needs to be created
In the Firebase console → Firestore → Indexes → Composite tab, add a collection-group index (not collection-scoped) on wallet_transactions:

Fields: type (Ascending) + createdAt (Descending) — covers filtered+sorted transaction lists
Fields: createdAt (Descending) alone, collection-group scoped — covers the unfiltered case
(If /admin/api/users?status=blocked|active also 500s, you'll additionally need a composite index on users: isBlocked + createdAt.)

The fastest way to get the exact index definition is to trigger the error once and check your app logs for the FAILED_PRECONDITION message — it contains a one-click "create this index" link.

Want me to also add log.error(...) in those repository catch blocks (currently the real Firestore exception is swallowed from logs, not just the response) and check in a firestore.indexes.json so these indexes are defined as code for future deploys?*/


/*
./deploy.sh \
        --jar target/app.jar \
        --host "2406:da1a:4d8:1d01:9666:e8e5:4a25:1f48" \
        --key /home/sandeep/Downloads/mainvm.pem \
        --bucket makecall-jar-bucket \
        --skip-build

3STOBvpAUqyivS1G5ZwvAvn+bRJpdMFtNzm6O5X6
867344465598*/
/*ssh -i ~/Downloads/mainvm.pem ec2-user@13.235.248.57*/


/*ssh -i "D:\mainvm.pem" ec2-user@13.232.110.237
 */