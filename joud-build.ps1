# تصفية الشاشة وتأمين البيئة
Clear-Host

# ─── الإعدادات الثابتة للمشروع ───
$MVN_PATH = "c:\joud_erp\.maven\apache-maven-3.9.6\bin\mvn.cmd"
$POM_PATH = "c:\joud_erp\pom.xml"

# التحقق من وجود أداة مافن في المسار المحدد
if (-not (Test-Path $MVN_PATH)) {
    Write-Error "Error: Maven Maven not found at $MVN_PATH"
    Exit
}

function Show-Menu {
    Write-Host "==================================================" -ForegroundColor Cyan
    Write-Host "         Joud ERP - Maven Build Controller        " -ForegroundColor Cyan
    Write-Host "==================================================" -ForegroundColor Cyan
    Write-Host "1) Validate      - Check project structure and POMs"
    Write-Host "2) Compile       - Compile Java source code files"
    Write-Host "3) Package       - Package modules into (JAR / WAR)"
    Write-Host "4) Install       - Build and install to local .m2 repository"
    Write-Host "5) Clean         - Delete 'target' folders (For Archive)"
    Write-Host "6) Clean Install - Full fresh build & force updates"
    Write-Host "7) Running: Tomcat and Install joud_erp.war..."
    Write-Host "8) Stop: Tomcat ..."
    Write-Host "9) Exit"
    Write-Host "==================================================" -ForegroundColor Cyan
}

do {
    Show-Menu
    $choice = Read-Host "Enter your choice (1-9)"
    Write-Host ""

    switch ($choice) {
        "1" {
            Write-Host "⏳ Running: Validate..." -ForegroundColor Yellow
            & $MVN_PATH validate -f $POM_PATH
        }
        "2" {
            Write-Host "⏳ Running: Compile..." -ForegroundColor Yellow
            & $MVN_PATH compile -f $POM_PATH
        }
        "3" {
            Write-Host "⏳ Running: Package..." -ForegroundColor Yellow
            & $MVN_PATH package -f $POM_PATH
        }
        "4" {
            Write-Host "⏳ Running: Install..." -ForegroundColor Yellow
            & $MVN_PATH install -f $POM_PATH
        }
        "5" {
            Write-Host "⏳ Running: Clean (Preparing for Archive)..." -ForegroundColor Yellow
            & $MVN_PATH clean -f $POM_PATH
        }
        "6" {
            Write-Host "⏳ Running: Clean and Install (-U Force Update)..." -ForegroundColor Yellow
            & $MVN_PATH clean install -U -f $POM_PATH
        }
        
        "7" {
            Write-Host "⏳ Running: Tomcat and Install joud_erp.war..." -ForegroundColor Yellow
            & $MVN_PATH cargo:run -U-f "c:\joud_erp\joud-app\pom.xml"
        }
        "8" {
            Write-Host "⏳ Stop: Tomcat ..." -ForegroundColor Yellow
            & $MVN_PATH cargo:stop -f "c:\joud_erp\joud-app\pom.xml"
        }
        "9" {
            Write-Host "Goodbye! Good luck with Joud ERP." -ForegroundColor Green
            break
        }
        Default {
            Write-Host "❌ Invalid choice, please select from 1 to 7." -ForegroundColor Red
        }
    }
    
    if ($choice -ne "9") {
        Write-Host ""
        Read-Host "Press Enter to continue..."
        Clear-Host
    }

} while ($choice -ne "9")5