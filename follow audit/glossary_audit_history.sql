-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:05 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `glossary_audit_history`
--

CREATE TABLE `glossary_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `glossary_audit_history`
--

INSERT INTO `glossary_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 119, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Diana Wilson', 'System', '2025-11-02 15:38:21', '2025-11-02 15:38:21'),
(1, 120, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Steward', 'System', '2025-11-02 15:38:21', '2025-11-02 15:38:21'),
(1, 121, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'System', '2025-11-02 15:38:21', '2025-11-02 15:38:21'),
(1, 122, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Active', 'Not Active', 'System', '2025-11-02 15:39:01', '2025-11-02 15:39:01'),
(1, 123, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Active', 'Not Active', 'System', '2025-11-02 15:39:11', '2025-11-02 15:39:11'),
(1, 124, 'Stakeholder', 'edit', 'Updated', 'Name', 'Diana Wilson', 'Alice Smith', 'System', '2025-11-02 15:39:24', '2025-11-02 15:39:24'),
(1, 125, 'Stakeholder', 'edit', 'Updated', 'Role Status', 'Not Active', 'Active', 'System', '2025-11-02 15:39:24', '2025-11-02 15:39:24'),
(1, 126, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Not Active', 'Yes', 'System', '2025-11-02 15:47:41', '2025-11-02 15:47:41'),
(1, 127, 'Stakeholder', 'status', 'Accepted', 'Role Status', 'Active', 'Yes', 'System', '2025-11-02 15:47:45', '2025-11-02 15:47:45'),
(6, 166, 'Glossary', 'Details', 'Updated', 'Parent Glossary', 'ccccx', 'Product', 'Alice Smith', '2025-11-13 14:44:15', '2025-11-13 14:44:15'),
(26, 18, 'Glossary', 'Details', 'Added', 'Name', NULL, 'fhfyjfhfghf', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 19, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'fghfhshfd', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 20, 'Glossary', 'Details', 'Added', 'Description', NULL, 'ghfghfghf', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 21, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 22, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 23, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 24, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 25, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 26, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 27, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 28, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 29, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(26, 30, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 15:29:03', '2025-10-23 15:29:03'),
(27, 31, 'Glossary', 'Details', 'Added', 'Name', NULL, 'njhkhjk', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 32, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'jhk', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 33, 'Glossary', 'Details', 'Added', 'Description', NULL, 'jhk', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 34, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 35, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 36, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 37, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 38, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 39, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 40, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 41, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 42, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 43, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-23 16:39:30', '2025-10-23 16:39:30'),
(27, 51, 'Glossary', 'Details', 'Updated', 'Name', 'njhkhjk', 'njhkhjk22', 'Alice Smith', '2025-10-27 12:02:17', '2025-10-27 12:02:17'),
(27, 52, 'Glossary', 'Details', 'Updated', 'Format Type', 'Text', 'Numeric', 'Alice Smith', '2025-10-27 12:16:18', '2025-10-27 12:16:18'),
(28, 53, 'Glossary', 'Details', 'Added', 'Name', NULL, 'adasdfsd', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 54, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL1761826395855', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 55, 'Glossary', 'Details', 'Added', 'Description', NULL, 'fsdfsdf', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 56, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 57, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 58, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 59, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 60, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 61, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 62, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 63, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Definition Owner', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 64, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(28, 65, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:13:16', '2025-10-30 15:13:16'),
(29, 66, 'Glossary', 'Details', 'Added', 'Name', NULL, 'gdfgdfg', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 67, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL002', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 68, 'Glossary', 'Details', 'Added', 'Description', NULL, 'dfgdfdf', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 69, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 70, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 71, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 72, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-30 15:17:32', '2025-10-30 15:17:32'),
(29, 73, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(29, 74, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(29, 75, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(29, 76, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Definition Owner', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(29, 77, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(29, 78, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:17:33', '2025-10-30 15:17:33'),
(30, 79, 'Glossary', 'Details', 'Added', 'Name', NULL, 'اتلانتن', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 80, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL003', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 81, 'Glossary', 'Details', 'Added', 'Description', NULL, 'تانلا', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 82, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 83, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 84, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 85, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 86, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 87, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 88, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 89, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Definition Owner', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 90, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(30, 91, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:17:48', '2025-10-30 15:17:48'),
(31, 92, 'Glossary', 'Details', 'Added', 'Name', NULL, 'تانتان', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 93, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL004', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 94, 'Glossary', 'Details', 'Added', 'Description', NULL, 'تالنتان', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 95, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 96, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 97, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 98, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 99, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-30 15:18:25', '2025-10-30 15:18:25'),
(31, 100, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 15:18:26', '2025-10-30 15:18:26'),
(31, 101, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:18:26', '2025-10-30 15:18:26'),
(31, 102, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Definition Owner', 'Alice Smith', '2025-10-30 15:18:26', '2025-10-30 15:18:26'),
(31, 103, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:18:26', '2025-10-30 15:18:26'),
(31, 104, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:18:26', '2025-10-30 15:18:26'),
(32, 105, 'Glossary', 'Details', 'Added', 'Name', NULL, 'يبليبليب', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 106, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL006', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 107, 'Glossary', 'Details', 'Added', 'Description', NULL, 'يبليبليب', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 108, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 109, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 110, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 111, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 112, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 113, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 114, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 115, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Definition Owner', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 116, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 117, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-30 15:19:11', '2025-10-30 15:19:11'),
(32, 118, 'Stakeholder', 'edit', 'Updated', 'Name', 'Alice Smith', 'Charlie Brown', 'System', '2025-10-30 15:20:59', '2025-10-30 15:20:59'),
(33, 128, 'Glossary', 'Details', 'Added', 'Name', NULL, 'glossary_test4', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 129, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL007', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 130, 'Glossary', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 131, 'Glossary', 'Details', 'Added', 'Format', NULL, 'format desc', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 132, 'Glossary', 'Details', 'Added', 'LDM', NULL, 'LDM Ref', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 133, 'Glossary', 'Details', 'Added', 'Business Logic', NULL, 'bus logic', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 134, 'Glossary', 'Details', 'Added', 'Examples', NULL, 'examplessss', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 135, 'Glossary', 'Details', 'Added', 'Parent Glossary', NULL, 'glossary test 3', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 136, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 137, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 138, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Domain', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 139, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Date', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 140, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Public', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 141, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 142, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 143, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Owner', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 144, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 145, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 11:02:49', '2025-11-09 11:02:49'),
(33, 146, 'Glossary', 'Details', 'Updated', 'Description', '9/11', '9///11', 'Alice Smith', '2025-11-09 12:22:17', '2025-11-09 12:22:17'),
(33, 147, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(33, 148, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Glossary Owner', NULL, 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(33, 149, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(33, 150, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Diana Wilson', 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(33, 151, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Steward', 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(33, 152, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-11-09 12:24:31', '2025-11-09 12:24:31'),
(34, 153, 'Glossary', 'Details', 'Added', 'Name', NULL, 'efef', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 154, 'Glossary', 'Details', 'Added', 'Reference Number', NULL, 'GL008', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 155, 'Glossary', 'Details', 'Added', 'Description', NULL, 'efdge', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 156, 'Glossary', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 157, 'Glossary', 'Details', 'Added', 'Lifecycle', NULL, 'Retired', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 158, 'Glossary', 'Details', 'Added', 'Type', NULL, 'Business', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 159, 'Glossary', 'Details', 'Added', 'Format Type', NULL, 'Text', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 160, 'Glossary', 'Details', 'Added', 'Security Classification', NULL, 'Confidential', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 161, 'Glossary', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 162, 'Glossary', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 163, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Glossary Owner', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 164, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Not Active', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34'),
(34, 165, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-12 12:53:34', '2025-11-12 12:53:34');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `glossary_audit_history`
--
ALTER TABLE `glossary_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `glossary_audit_history`
--
ALTER TABLE `glossary_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=167;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `glossary_audit_history`
--
ALTER TABLE `glossary_audit_history`
  ADD CONSTRAINT `glossary_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `glossary` (`ID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
