# Joud ERP - Enterprise Resource Planning System

Joud ERP is a comprehensive, modern Enterprise Resource Planning system built with Laravel and Vue.js, designed to manage core business operations including Accounting, CRM, Inventory, HR, and more.

## 🚀 Key Features

- **Accounting Management**: General Ledger, Chart of Accounts, Trial Balance, Financial Reports.
- **CRM**: Customer & Supplier Management, Sales Orders, Quotations.
- **Inventory Management**: Item Management, Stock Levels, Stock Adjustments.
- **Human Resources**: Employee Management, Attendance, Payroll.
- **Point of Sale (POS)**: Fast and efficient checkout system.
- **Reporting**: Advanced reporting engine with customizable dashboards.

## 🛠️ Built With

- **Backend**: [Laravel](https://laravel.com/)
- **Frontend**: [Vue.js](https://vuejs.org/)
- **UI Kit**: [Vuetify](https://vuetifyjs.com/)
- **Authentication**: Laravel Sanctum / JWT
- **Database**: MySQL / PostgreSQL

## 📋 Prerequisites

- PHP >= 8.0
- Composer
- Node.js >= 16.0
- MySQL >= 8.0
- Web Server (Apache/Nginx)

## 🚀 Quick Start

### 1. Installation

Clone the repository and install dependencies:

```bash
# Clone the repository
git clone <repository-url>

# Navigate to the project directory
cd joud_erp

# Install PHP dependencies
composer install --prefer-dist --no-dev

# Install Node.js dependencies
npm install
```

### 2. Database Setup

Create a `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Configure your database credentials in the `.env` file:

```env
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=joud_erp
DB_USERNAME=root
DB_PASSWORD=
```

### 3. Database Migration

Run the migrations to create the database tables:

```bash
# Create database
php artisan db:create

# Run migrations
php artisan migrate --force
```

### 4. Compile Assets

Compile the frontend assets:

```bash
# Development mode
npm run dev

# Production mode (minified)
npm run build
```

### 5. Start Development Server

Start the Laravel development server:

```bash
php artisan serve
```

Access the application at `http://localhost:8000`

## 🔒 Authentication

Create an admin user:

```bash
php artisan db:seed --class=AdminUserSeeder
```

Default credentials:
- Email: [EMAIL_ADDRESS]`
- Password: `password` (change immediately after login)

## 📂 Project Structure

```
joud_erp/
├── app/                    # Application logic (Models, Controllers, etc.)
├── bootstrap/              # Framework bootstrapping
├── config/                 # Configuration files
├── database/               # Database migrations and seeds
├── public/                 # Publicly accessible files
├── resources/              # Frontend assets (Vue.js, CSS, JS)
├── routes/                 # Route definitions
├── storage/                # Storage files
└── tests/                  # Testing
```

## 🔧 Development

### Adding New Features

1. **Backend**: Create a new model, controller, and routes in `app/` and `routes/`.
2. **Database**: Add migrations in `database/migrations/` if needed.
3. **Frontend**: Create components in `resources/js/components/` and update the relevant pages.
4. **API**: Register new API endpoints in `routes/api.php`.
5. **Permissions**: Update `database/seeders/RolesAndPermissionsSeeder.php` if needed.

### Testing

Run the test suite:

```bash
# Run all tests
php artisan test

# Run specific test
php artisan test tests/Feature/ExampleTest.php
```

## 🌐 Translation Management

The application supports multiple languages. To add or modify translations:

1. Translations are stored in `resources/lang/{locale}/`.
2. Key-value pairs are defined in PHP files (e.g., `messages.php`).
3. Vue components use the `{{ __('key.subkey') }}` syntax for translation.

## 🛡️ Security

- Keep your dependencies updated: `composer update` and `npm update`.
- Change default admin password immediately after installation.
- Enable HTTPS in production.
- Implement proper rate limiting and security headers.

## 📄 License

This project is open-sourced software licensed under the [MIT license](https://opensource.org/licenses/MIT).

## 🤝 Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests.

## 👨‍💻 Authors

- [Your Name] - Initial development
- [Add Contributors] - Additional features and improvements

## 🙏 Acknowledgments

- [Laravel Framework](https://laravel.com/)
- [Vue.js Framework](https://vuejs.org/)
- [Vuetify Component Framework](https://vuetifyjs.com/)
- [All open-source contributors]

## ❓ Troubleshooting

**Issue**: "Command not found" for artisan commands
**Solution**: Ensure you are in the project root directory and have run `composer install`.

**Issue**: Database connection errors
**Solution**: Verify your `.env` file has correct credentials and the database exists.

**Issue**: Frontend assets not loading
**Solution**: Run `npm run dev` (development) or `npm run build` (production).

## 📝 Support

If you encounter any issues or have questions, please open an issue on the GitHub repository.

---

*Made with ❤️ using Laravel and Vue.js*
