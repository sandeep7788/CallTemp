# Feature Specification: Terraform Infrastructure Validation & Production Deployment

## Title

Enhance and Validate Terraform Infrastructure for Production Deployment of **makecall.in**

## Objective

Review, fix, and improve the existing Terraform infrastructure so that it performs a complete production-ready deployment without manual intervention.

The current Terraform configuration already:

* Creates AWS infrastructure.
* Provisions required AWS resources.
* Launches the EC2 instance.
* Deploys and starts the Spring Boot application.
* Read pom.xml for required dependencies.

The remaining deployment steps should also be fully automated.

---

## Scope

### 1. Terraform Validation

Perform a complete review of all existing Terraform files.

Requirements:

* Validate every Terraform module.
* Remove duplicate or unused resources.
* Fix incorrect resource dependencies.
* Ensure resources are created in the correct order.
* Improve maintainability without changing the existing architecture.
* Preserve existing infrastructure wherever possible.

---

### 2. EC2 Provisioning

Ensure the EC2 instance is fully configured after provisioning.

Automatically:

* Update the operating system.
* Install required packages.
* Configure Java runtime.
* Configure application directories.
* Configure system services.
* Ensure the application starts automatically after reboot.

---

### 3. Nginx Installation

Automatically install Nginx during provisioning.

Requirements:

* Install the latest stable version.
* Enable the Nginx service.
* Start the service automatically.
* Configure automatic startup after reboot.

---

### 4. Reverse Proxy Configuration

Configure Nginx as a reverse proxy.

Requirements:

* Forward HTTPS requests to the Spring Boot application.
* Configure proper proxy headers.
* Support WebSocket connections if required.
* Configure request timeouts.
* Configure error pages.
* Optimize for production usage.

---

### 5. SSL Certificate

Automatically configure HTTPS using Let's Encrypt.

Requirements:

* Install Certbot.

* Generate an SSL certificate for:

  **makecall.in**

* Use the following email for certificate registration:

  **[s.pareekpro@gmail.com](mailto:s.pareekpro@gmail.com)**

* Configure automatic certificate renewal.

* Verify renewal configuration.

* Prevent duplicate certificate creation.

---

### 6. HTTPS Configuration

Ensure HTTPS is fully functional.

Requirements:

* Redirect all HTTP traffic to HTTPS.
* Enable TLS using the generated certificate.
* Configure secure SSL settings.
* Remove insecure configurations.
* Verify SSL works after deployment.

---

### 7. Domain Validation

Ensure the infrastructure is correctly configured for:

* makecall.in
* [www.makecall.in](http://www.makecall.in) (if applicable)

Validate:

* DNS resolution
* Reverse proxy
* SSL configuration
* Application accessibility

---

### 8. Application Deployment

Ensure the application deployment is reliable.

Requirements:

* Verify application startup.
* Verify application health.
* Restart automatically if the application crashes.
* Configure proper system service management.
* Ensure logs are accessible.

---

### 9. Security Improvements

Review the infrastructure for security best practices.

Validate:

* Security Groups
* IAM permissions
* File permissions
* Nginx security headers
* Open ports
* Firewall configuration
* SSH configuration

Only expose the required ports.

---

### 10. Terraform Improvements

Improve the existing Terraform code.

Requirements:

* Remove unused variables.
* Remove unused outputs.
* Remove duplicate resources.
* Improve module structure.
* Improve readability.
* Ensure idempotent execution.
* Avoid unnecessary resource recreation.

---

### 11. Deployment Automation

The deployment should require only:

```bash
terraform init
terraform plan
terraform apply
```

After Terraform completes, the infrastructure should automatically:

* Provision AWS resources.
* Configure the EC2 instance.
* Install Java.
* Install Nginx.
* Configure the reverse proxy.
* Generate the SSL certificate.
* Enable HTTPS.
* Start the Spring Boot application.
* Verify the application is running.

No manual SSH steps should be required.

---

### 12. Validation

Review the complete deployment process and verify:

* Terraform execution
* Resource creation
* EC2 provisioning
* Application startup
* Nginx installation
* Reverse proxy configuration
* SSL generation
* HTTPS access
* Automatic certificate renewal
* System service configuration

Fix any issues found during validation.

---

### 13. Code Cleanup

* Remove obsolete Terraform resources.
* Remove duplicate provisioning scripts.
* Simplify shell scripts where possible.
* Improve comments only where necessary.
* Keep the infrastructure clean and maintainable.

---

## Technical Constraints

* Modify the existing Terraform project only.
* Do not rewrite the infrastructure from scratch.
* Preserve the existing AWS architecture.
* Reuse existing resources whenever possible.
* Keep provisioning fully automated.
* Documentation should remain minimal.
* Do not create unnecessary Terraform modules.

---

## Acceptance Criteria

* Terraform provisions all required AWS resources successfully.
* EC2 is fully configured without manual intervention.
* Nginx is installed and configured as a reverse proxy.
* The Spring Boot application is accessible through Nginx.
* A valid Let's Encrypt SSL certificate is automatically generated for **makecall.in** using **[s.pareekpro@gmail.com](mailto:s.pareekpro@gmail.com)**.
* HTTP requests are automatically redirected to HTTPS.
* SSL certificates renew automatically.
* The application is accessible securely over **[https://makecall.in](https://makecall.in)**.
* Terraform can be executed multiple times without breaking existing resources or recreating them unnecessarily.
* The deployment is fully automated, production-ready, and requires no manual post-deployment configuration.
